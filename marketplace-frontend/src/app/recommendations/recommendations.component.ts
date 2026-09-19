import { CommonModule } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, Input, OnDestroy, OnInit, Output, EventEmitter, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Subscription } from 'rxjs';
import { ApiService } from '../api.service';
import { Part, RecommendationFeed, Vehicle, VinDecodeResult } from '../models';

@Component({
  selector: 'app-recommendations',
  imports: [CommonModule, FormsModule],
  templateUrl: './recommendations.component.html',
  styleUrl: './recommendations.component.css'
})
export class RecommendationsComponent implements OnInit, OnDestroy {
  @Input() vehicles: Vehicle[] = [];
  @Input() settingsOnly = false;
  @Output() openListing = new EventEmitter<Part>();
  private readonly api = inject(ApiService);
  private readonly subscriptions = new Subscription();
  private feedRequest?: Subscription;
  readonly feed = signal<RecommendationFeed | null>(null);
  readonly loading = signal(false);
  readonly ready = signal(false);
  readonly busy = signal(false);
  readonly error = signal('');
  readonly notice = signal('');
  readonly editorOpen = signal(false);
  readonly confirmClear = signal(false);
  readonly vinLoading = signal(false);
  readonly vinResult = signal<VinDecodeResult | null>(null);
  enabled = true;
  selectedIds: number[] = [];
  vehicleSearch = '';
  vin = '';

  ngOnInit(): void {
    this.editorOpen.set(this.settingsOnly);
    this.loadSettings();
    if (!this.settingsOnly) this.refresh();
  }
  ngOnDestroy(): void { this.subscriptions.unsubscribe(); this.feedRequest?.unsubscribe(); }
  loadSettings(): void {
    this.error.set('');
    this.subscriptions.add(this.api.recommendationSettings().subscribe({
      next: settings => {
        this.enabled = settings.personalizationEnabled;
        this.selectedIds = settings.vehicles.map(vehicle => vehicle.id);
        this.ready.set(true);
      }, error: e => this.fail(e, 'Could not load your recommendation settings. Please retry.')
    }));
  }
  refresh(): void {
    this.feedRequest?.unsubscribe();
    this.loading.set(true);
    this.error.set('');
    this.feedRequest = this.api.recommendations().subscribe({
      next: feed => { this.feed.set(feed); this.loading.set(false); },
      error: e => { this.feed.set(null); this.loading.set(false); this.fail(e, 'Recommendations are unavailable. You can still browse the marketplace.'); }
    });
  }
  retry(): void { if (!this.ready()) this.loadSettings(); if (!this.settingsOnly) this.refresh(); }
  visibleVehicles(): Vehicle[] {
    const query = this.vehicleSearch.trim().toLowerCase();
    return this.vehicles.filter(vehicle => this.vehicleLabel(vehicle).toLowerCase().includes(query));
  }
  vehicleLabel(vehicle: Vehicle): string {
    return [vehicle.brand, vehicle.model, vehicle.year, vehicle.engine, vehicle.fuelType].filter(Boolean).join(' · ');
  }
  imageUrl(url: string): string { return this.api.assetUrl(url); }
  toggleVehicle(id: number, checked: boolean): void {
    this.selectedIds = checked ? [...new Set([...this.selectedIds, id])] : this.selectedIds.filter(value => value !== id);
  }
  decodeVin(): void {
    const vin = this.vin.trim().toUpperCase();
    if (!/^[A-HJ-NPR-Z0-9]{17}$/.test(vin)) {
      this.error.set('Enter a valid 17-character VIN without I, O, or Q.');
      this.vinResult.set(null);
      return;
    }
    this.vinLoading.set(true);
    this.error.set('');
    this.notice.set('');
    this.subscriptions.add(this.api.decodeVin(vin).subscribe({
      next: result => {
        this.vinResult.set(result);
        const knownMatches = (result.matchedVehicleIds ?? []).filter(id => this.vehicles.some(vehicle => vehicle.id === id));
        const availableSlots = Math.max(0, 5 - this.selectedIds.length);
        const additions = knownMatches.filter(id => !this.selectedIds.includes(id)).slice(0, availableSlots);
        this.selectedIds = [...new Set([...this.selectedIds, ...additions])];
        this.vinLoading.set(false);
        if (additions.length) this.notice.set('Catalog vehicle matched and selected. Save preferences to keep it.');
      },
      error: (error: HttpErrorResponse) => {
        this.vinLoading.set(false);
        this.vinResult.set(null);
        this.error.set(error.error?.message || 'The VIN could not be decoded. Enter your vehicle manually.');
      }
    }));
  }
  save(): void {
    if (!this.ready() || this.busy()) return;
    if (this.selectedIds.length > 5) { this.error.set('Choose up to five vehicles.'); return; }
    this.busy.set(true); this.error.set(''); this.notice.set('');
    this.subscriptions.add(this.api.saveRecommendationSettings(this.enabled, this.selectedIds).subscribe({
      next: settings => {
        this.enabled = settings.personalizationEnabled;
        this.selectedIds = settings.vehicles.map(vehicle => vehicle.id);
        this.busy.set(false);
        this.notice.set('Preferences saved. Vehicle choices are used even when activity-based personalization is off.');
        if (!this.settingsOnly) this.refresh();
      }, error: e => { this.busy.set(false); this.fail(e, 'Could not save preferences. Your previous settings are unchanged.'); }
    }));
  }
  clearHistory(): void {
    if (this.busy()) return;
    this.busy.set(true); this.error.set('');
    this.subscriptions.add(this.api.clearRecommendationHistory().subscribe({
      next: () => {
        this.busy.set(false); this.confirmClear.set(false);
        this.notice.set('Recommendation history cleared. Your saved parts and conversations have not been deleted.');
        if (!this.settingsOnly) this.refresh();
      }, error: e => { this.busy.set(false); this.fail(e, 'Could not clear history. Please retry.'); }
    }));
  }
  private fail(error: { status?: number }, fallback: string): void {
    this.error.set(error.status === 401 || error.status === 403 ? 'Sign in with an active buyer account to use recommendations.' : fallback);
  }
}
