import { Component, inject } from '@angular/core';
import { RouterLink } from '@angular/router';
import { AuthService } from '../../../services/auth-service';

@Component({
  selector: 'app-sign-in',
  imports: [RouterLink],
  templateUrl: './sign-in.html',
  styles: ``,
})
export class SignIn {
  private authService = inject(AuthService);

  login() {
    this.authService.login();
  }
}
