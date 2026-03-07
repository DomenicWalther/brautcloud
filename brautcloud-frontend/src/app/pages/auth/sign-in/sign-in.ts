import { Component, inject, signal } from '@angular/core';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { AuthService } from '../../../services/auth-service';
import { form, FormField } from '@angular/forms/signals';
import { AuthDTO } from '../../../core/models/auth-dto';

@Component({
  selector: 'app-sign-in',
  imports: [RouterLink, FormField],
  templateUrl: './sign-in.html',
  styles: ``,
})
export class SignIn {
  private authService = inject(AuthService);
  private router = inject(Router);
  private route = inject(ActivatedRoute);

  loginModel = signal<AuthDTO>({
    email: '',
    password: '',
  });

  loginForm = form(this.loginModel);

  onSubmit(event: Event) {
    event.preventDefault();
    const { email, password } = this.loginModel();
    this.login({ email, password });
  }

  login({ email, password }: AuthDTO) {
    this.authService
      .login({
        email,
        password,
      })
      .subscribe({
        next: () => {
          const returlUrl = this.route.snapshot.queryParamMap.get('returnUrl') ?? '/app/home';
          this.router.navigateByUrl(returlUrl);
        },
      });
  }
}
