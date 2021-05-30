import {NgModule} from '@angular/core';
import {RouterModule, Routes} from '@angular/router';
import {NamespaceComponent} from './components/namespace/namespace.component';
import {ConfigComponent} from './components/config/config.component';
import {ServiceComponent} from './components/service/service.component';
import {DashboardComponent} from './components/dashboard/dashboard.component';

const routes: Routes = [
  {path: '', pathMatch: 'full', redirectTo: '/dashboard'},
  {path: 'dashboard', component: DashboardComponent},
  {path: 'namespace', component: NamespaceComponent},
  {path: 'config', component: ConfigComponent},
  {path: 'service', component: ServiceComponent}
];

@NgModule({
  imports: [RouterModule.forRoot(routes)],
  exports: [RouterModule]
})
export class AppRoutingModule {
}
