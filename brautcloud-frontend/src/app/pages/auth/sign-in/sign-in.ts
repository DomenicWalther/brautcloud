import { Component, inject, signal } from '@angular/core';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { AuthService } from '../../../services/auth-service';
import { email, form, FormField, required, validate } from '@angular/forms/signals';
import { AuthDTO } from '../../../core/models/auth-dto';
import { HttpErrorResponse } from '@angular/common/http';
import { authSchema } from '../schemas/auth.schema';

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

  private serverError = signal<string | null>(null);

  private loginModel = signal<AuthDTO>({
    email: '',
    password: '',
  });

  loginForm = form(this.loginModel, (schemaPath) => {
    authSchema(schemaPath, this.serverError);
  });

  onSubmit(event: Event) {
    event.preventDefault();
    this.serverError.set(null);
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
        error: (error: HttpErrorResponse) => {
          if (error.status === 403) {
            this.serverError.set('Invalid email or password');
          }
        },
      });
  }
}
