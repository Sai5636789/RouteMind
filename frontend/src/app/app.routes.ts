import { Routes } from '@angular/router';
import { DashboardComponent } from './features/dashboard/dashboard.component';
import { DriverListComponent } from './features/drivers/driver-list.component';
import { OrderListComponent } from './features/orders/order-list.component';
import { VehicleListComponent } from './features/vehicles/vehicle-list.component';

export const routes: Routes = [
  { path: '', component: DashboardComponent },
  { path: 'vehicles', component: VehicleListComponent },
  { path: 'drivers', component: DriverListComponent },
  { path: 'orders', component: OrderListComponent },
  { path: '**', redirectTo: '' },
];
