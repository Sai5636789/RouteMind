import { BreakpointObserver, Breakpoints } from '@angular/cdk/layout';
import { Component, inject, signal } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatListModule } from '@angular/material/list';
import { MatSidenavModule } from '@angular/material/sidenav';
import { MatToolbarModule } from '@angular/material/toolbar';
import { Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { map } from 'rxjs/operators';
import { AuthService } from './core/services/auth.service';
import { TrackingService } from './core/services/tracking.service';

interface NavItem {
  path: string;
  label: string;
  icon: string;
}

@Component({
  selector: 'app-root',
  imports: [
    MatButtonModule,
    MatIconModule,
    MatListModule,
    MatSidenavModule,
    MatToolbarModule,
    RouterLink,
    RouterLinkActive,
    RouterOutlet,
  ],
  templateUrl: './app.html',
  styleUrl: './app.scss',
})
export class App {
  private readonly breakpointObserver = inject(BreakpointObserver);
  protected readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  private readonly tracking = inject(TrackingService);

  protected readonly isHandset = signal(false);
  protected readonly navItems: NavItem[] = [
    { path: '/', label: 'Overview', icon: 'dashboard' },
    { path: '/vehicles', label: 'Vehicles', icon: 'local_shipping' },
    { path: '/drivers', label: 'Drivers', icon: 'person' },
    { path: '/orders', label: 'Orders', icon: 'assignment' },
    { path: '/dispatch', label: 'Dispatch', icon: 'route' },
    { path: '/map', label: 'Fleet map', icon: 'map' },
  ];

  constructor() {
    this.breakpointObserver
      .observe(Breakpoints.Handset)
      .pipe(map((result) => result.matches))
      .subscribe((handset) => this.isHandset.set(handset));
  }

  /** Sign out: drop the token, stop live tracking, and go to the login page. */
  protected logout(): void {
    this.tracking.disconnect();
    this.auth.logout();
    void this.router.navigate(['/login']);
  }
}
