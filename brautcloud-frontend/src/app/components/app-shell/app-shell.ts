import { Component, computed, inject } from '@angular/core';
import { RouterLink, RouterLinkActive } from '@angular/router';
import { AUTH_SERVICE } from '../../core/tokens';
import { UserService } from '../../services/user-service';

@Component({
  selector: 'app-shell',
  imports: [RouterLink, RouterLinkActive],
  templateUrl: './app-shell.html',
  styleUrl: './app-shell.css',
})
export class AppShell {
  private readonly authService = inject(AUTH_SERVICE, { optional: true });
  private readonly userService = inject(UserService, { optional: true });

  protected readonly isSigningOut = computed(() => this.authService?.isLoggingOut() ?? false);

  constructor() {
    this.userService?.reload?.();
  }

  protected signOut(): void {
    this.authService?.logout();
  }
}
