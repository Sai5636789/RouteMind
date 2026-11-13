import { Routes } from '@angular/router';
import { authGuard } from './core/guards/auth.guard';
import { LoginComponent } from './features/auth/login.component';
import { DashboardComponent } from './features/dashboard/dashboard.component';
import { DriverListComponent } from './features/drivers/driver-list.component';
import { OrderListComponent } from './features/orders/order-list.component';
import { VehicleListComponent } from './features/vehicles/vehicle-list.component';

export const routes: Routes = [
  { path: 'login', component: LoginComponent },
  { path: '', component: DashboardComponent, canActivate: [authGuard] },
  { path: 'vehicles', component: VehicleListComponent, canActivate: [authGuard] },
  { path: 'drivers', component: DriverListComponent, canActivate: [authGuard] },
  { path: 'orders', component: OrderListComponent, canActivate: [authGuard] },
  // Lazy-loaded: Leaflet is large and only the map needs it, so it stays
  // out of the initial bundle.
  {
    path: 'map',
    loadComponent: () =>
      import('./features/map/fleet-map.component').then((m) => m.FleetMapComponent),
    canActivate: [authGuard],
  },
  { path: '**', redirectTo: '' },
];
