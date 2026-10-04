import { ChangeDetectionStrategy, Component, computed, inject } from '@angular/core';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { initials } from './core/labels';
import { DEMO_CUSTOMERS, Session } from './core/session';
import { Icon, IconName } from './shared/icon';

interface NavigationLink {
  path: string;
  label: string;
  icon: IconName;
}

/** The app shell: the brand, the customer's and the back office's pages, and who you are signed in as. */
@Component({
  selector: 'app-root',
  imports: [RouterOutlet, RouterLink, RouterLinkActive, Icon],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './app.html',
  styleUrl: './app.scss',
})
export class App {
  protected readonly session = inject(Session);
  protected readonly customers = DEMO_CUSTOMERS;
  protected readonly customerInitials = computed(() => initials(this.session.customer()));

  protected readonly customerLinks: NavigationLink[] = [
    { path: '/shop', label: 'Shop', icon: 'bag' },
    { path: '/my-orders', label: 'My orders', icon: 'receipt' },
  ];
  protected readonly backOfficeLinks: NavigationLink[] = [
    { path: '/orders', label: 'All orders', icon: 'package' },
    { path: '/operations', label: 'Operations', icon: 'shield' },
  ];
}
