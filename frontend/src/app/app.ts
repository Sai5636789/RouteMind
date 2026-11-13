import { BreakpointObserver, Breakpoints } from '@angular/cdk/layout';
import { Component, inject, signal } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatListModule } from '@angular/material/list';
import { MatSidenavModule } from '@angular/material/sidenav';
import { MatToolbarModule } from '@angular/material/toolbar';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { map } from 'rxjs/operators';

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

  protected readonly isHandset = signal(false);
  protected readonly navItems: NavItem[] = [
    { path: '/', label: 'Overview', icon: 'dashboard' },
    { path: '/vehicles', label: 'Vehicles', icon: 'local_shipping' },
    { path: '/drivers', label: 'Drivers', icon: 'person' },
    { path: '/orders', label: 'Orders', icon: 'assignment' },
    { path: '/map', label: 'Fleet map', icon: 'map' },
  ];

  constructor() {
    this.breakpointObserver
      .observe(Breakpoints.Handset)
      .pipe(map((result) => result.matches))
      .subscribe((handset) => this.isHandset.set(handset));
  }
}
