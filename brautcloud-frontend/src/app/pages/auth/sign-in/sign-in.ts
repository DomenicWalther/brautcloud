import { HttpErrorResponse } from '@angular/common/http';
import { Component, inject, signal } from '@angular/core';
import { form, FormField } from '@angular/forms/signals';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { AuthDTO } from '../../../core/models/auth.dto';
import { AuthRoutingService } from '../../../services/auth-routing-service';
import { AuthService } from '../../../services/auth-service';
import { authSchema } from '../schemas/auth.schema';
import { AuthShell } from '../../../components/auth-shell/auth-shell';
import { ToastService } from '../../../services/toast-service';

@Component({
  selector: 'app-sign-in',
  imports: [RouterLink, FormField, AuthShell],
  templateUrl: './sign-in.html',
  styles: ``,
})
export class SignIn {
  private readonly authService = inject(AuthService);
  private readonly authRouting = inject(AuthRoutingService);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);
  private readonly toastService = inject(ToastService);

  readonly serverError = signal<string | null>(null);
  readonly submitting = signal(false);
  readonly returnUrl = this.authRouting.safeReturnUrl(
    this.route.snapshot.queryParamMap.get('returnUrl'),
  );

  private readonly loginModel = signal<AuthDTO>({
    email: '',
    password: '',
  });

  readonly loginForm = form(this.loginModel, (schemaPath) => {
    authSchema(schemaPath);
  });

  onSubmit(event: Event): void {
    event.preventDefault();
    this.serverError.set(null);
    if (this.loginForm().invalid() || this.submitting()) {
      return;
    }

    this.login(this.loginModel());
  }

  login(credentials: AuthDTO): void {
    if (this.submitting()) {
      return;
    }

    this.submitting.set(true);
    this.authService.login(credentials).subscribe({
      next: () => {
        this.toastService.show('Signed in successfully.', 'success');
        const destination = this.authRouting.destinationAfterAuth(
          this.route.snapshot.queryParamMap.get('returnUrl'),
        );
        void this.router.navigateByUrl(destination);
      },
      error: (error: HttpErrorResponse) => {
        const message =
          error.error?.message ?? 'Unable to sign in. Please check your details and try again.';
        this.serverError.set(message);
        this.toastService.show(message, 'error');
        this.submitting.set(false);
      },
    });
  }
}
