import { Routes } from '@angular/router';

export const routes: Routes = [
  { path: '', pathMatch: 'full', redirectTo: 'shop' },
  {
    path: 'shop',
    title: 'Shop · Trailhead',
    loadComponent: () => import('./features/shop/shop-page').then((page) => page.ShopPage),
  },
  {
    path: 'my-orders',
    title: 'My orders · Trailhead',
    loadComponent: () =>
      import('./features/my-orders/my-orders-page').then((page) => page.MyOrdersPage),
  },
  {
    path: 'my-orders/:orderId',
    title: 'Your order · Trailhead',
    loadComponent: () =>
      import('./features/my-orders/customer-order-page').then((page) => page.CustomerOrderPage),
  },
  {
    path: 'orders',
    title: 'All orders · Trailhead',
    loadComponent: () => import('./features/orders/orders-page').then((page) => page.OrdersPage),
  },
  {
    path: 'orders/:orderId',
    title: 'Order · Trailhead',
    loadComponent: () =>
      import('./features/orders/order-detail-page').then((page) => page.OrderDetailPage),
  },
  {
    path: 'operations',
    title: 'Operations · Trailhead',
    loadComponent: () =>
      import('./features/operations/operations-page').then((page) => page.OperationsPage),
  },
  { path: '**', redirectTo: 'shop' },
];
