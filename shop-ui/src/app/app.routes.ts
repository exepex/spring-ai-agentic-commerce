import { Routes } from '@angular/router';

export const routes: Routes = [
  { path: '', pathMatch: 'full', redirectTo: 'shop' },
  { path: 'shop', loadComponent: () => import('./shop/shop').then((page) => page.Shop) },
  { path: 'orders', loadComponent: () => import('./orders/orders').then((page) => page.Orders) },
  { path: 'orders/:orderId', loadComponent: () => import('./orders/order-detail').then((page) => page.OrderDetail) },
  { path: 'operations', loadComponent: () => import('./operations/operations').then((page) => page.Operations) },
  { path: '**', redirectTo: 'shop' },
];
