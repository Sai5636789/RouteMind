import { Component, OnInit, inject, signal } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { forkJoin, of } from 'rxjs';
import { catchError } from 'rxjs/operators';
import { ApiHealth, DependencyHealth } from '../../core/models/health-status';
import { HealthService } from '../../core/services/health.service';

type LoadState = 'loading' | 'ready' | 'error';

@Component({
  selector: 'app-dashboard',
  imports: [MatButtonModule, MatCardModule, MatProgressSpinnerModule],
  templateUrl: './dashboard.component.html',
  styleUrl: './dashboard.component.scss',
})
export class DashboardComponent implements OnInit {
  private readonly healthService = inject(HealthService);

  protected readonly state = signal<LoadState>('loading');
  protected readonly apiHealth = signal<ApiHealth | null>(null);
  protected readonly dependencies = signal<DependencyHealth | null>(null);

  ngOnInit(): void {
    this.refresh();
  }

  protected refresh(): void {
    this.state.set('loading');
    forkJoin({
      api: this.healthService.getApiHealth().pipe(catchError(() => of(null))),
      deps: this.healthService.getDependencies().pipe(catchError(() => of(null))),
    }).subscribe(({ api, deps }) => {
      this.apiHealth.set(api);
      this.dependencies.set(deps);
      this.state.set(api ? 'ready' : 'error');
    });
  }

  protected dependencyStatus(name: string): string {
    return this.dependencies()?.components[name]?.status ?? 'UNKNOWN';
  }
}
