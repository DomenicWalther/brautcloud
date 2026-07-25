import { Component, inject } from '@angular/core';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { AuthService } from '../../../services/auth-service';

@Component({
  selector: 'app-authenticated-layout',
  imports: [RouterLink, RouterLinkActive, RouterOutlet],
  templateUrl: './authenticated-layout.html',
  styleUrl: './authenticated-layout.css',
})
export class AuthenticatedLayout {
  private readonly authService = inject(AuthService);

  protected readonly isSigningOut = this.authService.isLoggingOut;

  protected signOut(): void {
    this.authService.logout();
  }
}
