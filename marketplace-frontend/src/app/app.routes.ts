import { Routes } from '@angular/router';

// The app shell renders these views itself; declaring the paths keeps direct
// seller-profile URLs refreshable without duplicating the global layout.
export const routes: Routes = [
  { path: '', pathMatch: 'full', children: [] },
  { path: 'sellers/:id', children: [] },
  { path: 'parts/:id', children: [] },
  { path: 'auth/callback', children: [] },
  { path: '**', redirectTo: '' }
];
