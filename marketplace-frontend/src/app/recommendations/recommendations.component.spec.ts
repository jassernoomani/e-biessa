import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { RecommendationsComponent } from './recommendations.component';
import { Part } from '../models';

describe('Buyer recommendations', () => {
  const base = 'http://localhost:8080/api/recommendations';
  const vehicle = { id: 3, brand: 'Volkswagen', model: 'Golf', year: 2020, fuelType: 'Diesel' };
  beforeEach(() => TestBed.configureTestingModule({ imports: [RecommendationsComponent], providers: [provideHttpClient(), provideHttpClientTesting()] }));
  afterEach(() => TestBed.inject(HttpTestingController).verify());
  function setup(settingsOnly = false) {
    const fixture = TestBed.createComponent(RecommendationsComponent);
    fixture.componentRef.setInput('vehicles', [vehicle]);
    fixture.componentRef.setInput('settingsOnly', settingsOnly);
    fixture.detectChanges();
    const http = TestBed.inject(HttpTestingController);
    http.expectOne(`${base}/settings`).flush({ personalizationEnabled: true, vehicles: [] });
    return { fixture, http, component: fixture.componentInstance };
  }
  it('renders grounded reasons and opens the selected listing without a fake percent match', () => {
    const { fixture, http, component } = setup();
    const part = { id: 7, title: 'Golf caliper', price: 310, imageUrls: ['/uploads/caliper.jpg'], status: 'ACTIVE' } as unknown as Part;
    http.expectOne(base).flush({ mode: 'PERSONALIZED', results: [{ part, score: 20, reasons: ['Based on saved parts in this category'] }], caution: 'Confirm OEM with the seller.' });
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('Based on saved parts in this category');
    expect(fixture.nativeElement.textContent).not.toContain('% match');
    expect(fixture.nativeElement.querySelector('img').getAttribute('src')).toBe('http://localhost:8080/uploads/caliper.jpg');
    const open = vi.spyOn(component.openListing, 'emit');
    fixture.nativeElement.querySelector('.listing-link').click();
    expect(open).toHaveBeenCalledWith(part);
  });
  it('saves vehicle choices and opt-out then refreshes recommendations', () => {
    const { fixture, http, component } = setup();
    http.expectOne(base).flush({ mode: 'DISCOVER', results: [], caution: '' });
    component.enabled = false;
    component.toggleVehicle(vehicle.id, true);
    component.save();
    const save = http.expectOne(`${base}/settings`);
    expect(save.request.method).toBe('PUT');
    expect(save.request.body).toEqual({ personalizationEnabled: false, vehicleIds: [3] });
    save.flush({ personalizationEnabled: false, vehicles: [vehicle] });
    http.expectOne(base).flush({ mode: 'VEHICLE', results: [], caution: 'Confirm fitment' });
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('No new eligible parts');
  });
  it('clears only recommendation history with confirmation', () => {
    const { component, http } = setup(true);
    component.confirmClear.set(true);
    component.clearHistory();
    const clear = http.expectOne(`${base}/history`);
    expect(clear.request.method).toBe('DELETE');
    clear.flush(null);
    expect(component.confirmClear()).toBe(false);
    expect(component.notice()).toContain('have not been deleted');
  });
  it('limits vehicle choices and keeps errors recoverable', () => {
    const { component, http } = setup(true);
    component.selectedIds = [1,2,3,4,5,6];
    component.save();
    expect(component.error()).toContain('five');
    http.expectNone(`${base}/settings`);
    component.selectedIds = [3];
    component.save();
    http.expectOne(`${base}/settings`).flush({}, { status: 503, statusText: 'Unavailable' });
    expect(component.busy()).toBe(false);
    expect(component.error()).toContain('previous settings');
  });
  it('decodes a VIN and selects an exact catalog match without storing the VIN in settings', () => {
    const { fixture, component, http } = setup(true);
    component.vin = 'wvwzzz1kzaw000001';
    component.decodeVin();
    const request = http.expectOne('http://localhost:8080/api/vehicles/vin/WVWZZZ1KZAW000001');
    request.flush({ vin: 'WVWZZZ1KZAW000001', make: 'Volkswagen', model: 'Golf', year: 2020,
      engine: '2.0L · 4 cylinders', fuelType: 'Diesel', matchedVehicleIds: [3],
      warnings: ['VIN data does not guarantee part fitment.'] });
    fixture.detectChanges();
    expect(component.selectedIds).toEqual([3]);
    expect(component.notice()).toContain('Catalog vehicle matched');
    expect(fixture.nativeElement.querySelector('.vin-result').textContent).toContain('2020 Volkswagen Golf');
    expect(fixture.nativeElement.textContent).toContain('does not guarantee part fitment');
  });
  it('rejects malformed VINs before making a request', () => {
    const { component, http } = setup(true);
    component.vin = 'short';
    component.decodeVin();
    http.expectNone(request => request.url.includes('/vehicles/vin/'));
    expect(component.error()).toContain('17-character VIN');
  });
  it('offers retry on an unavailable feed', () => {
    const { fixture, http, component } = setup();
    http.expectOne(base).flush({}, { status: 503, statusText: 'Unavailable' });
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('[role=alert]').textContent).toContain('unavailable');
    component.retry();
    http.expectOne(base).flush({ mode: 'DISCOVER', results: [], caution: '' });
    expect(component.error()).toBe('');
  });
});
