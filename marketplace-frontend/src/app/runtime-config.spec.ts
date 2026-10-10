import { apiBaseUrl } from './runtime-config';
import { ApiService } from './api.service';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting, HttpTestingController } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

describe('Runtime deployment configuration', () => {
  afterEach(() => { delete window.EBIESSA_CONFIG; });

  it('preserves the existing local-development URL', () => {
    delete window.EBIESSA_CONFIG;
    expect(apiBaseUrl()).toBe('http://localhost:8080/api');
  });

  it('routes API calls, images and sockets through the demo origin', () => {
    window.EBIESSA_CONFIG = { apiBaseUrl: '/api' };
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    const api = TestBed.inject(ApiService);
    const http = TestBed.inject(HttpTestingController);
    api.categories().subscribe();
    http.expectOne(`${location.origin}/api/categories`).flush([]);
    expect(api.assetUrl('/uploads/test.png')).toBe(`${location.origin}/uploads/test.png`);
    expect(api.realtimeSocketUrl('abc')).toBe(`${location.origin.replace(/^http/, 'ws')}/ws/messages?ticket=abc`);
    expect(api.oauthStartUrl('google')).toBe(`${location.origin}/oauth2/authorization/google`);
    http.verify();
  });

  it('normalizes a configured absolute URL', () => {
    window.EBIESSA_CONFIG = { apiBaseUrl: 'https://example.test/api/' };
    expect(apiBaseUrl()).toBe('https://example.test/api');
  });

  it('rejects unsafe schemes and embedded credentials', () => {
    for (const value of ['javascript:alert(1)', 'https://user:secret@example.test/api', '/api?secret=x', '/api#x']) {
      window.EBIESSA_CONFIG = { apiBaseUrl: value };
      expect(() => apiBaseUrl()).toThrow('Invalid API URL configuration');
    }
  });
});
