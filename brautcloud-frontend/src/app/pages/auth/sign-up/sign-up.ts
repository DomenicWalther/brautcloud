import { HttpErrorResponse } from '@angular/common/http';
import { Component, inject, signal } from '@angular/core';
import { form, FormField, minLength, required, validate } from '@angular/forms/signals';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { AuthDTO } from '../../../core/models/auth.dto';
import { AuthRoutingService } from '../../../services/auth-routing-service';
import { AuthService } from '../../../services/auth-service';
import { authSchema } from '../schemas/auth.schema';
import { AuthShell } from '../../../components/auth-shell/auth-shell';

interface RegisterDTO {
  email: string;
  password: string;
  confirmPassword: string;
}

@Component({
  selector: 'app-sign-up',
  imports: [RouterLink, FormField, AuthShell],
  templateUrl: './sign-up.html',
  styles: ``,
})
export class SignUp {
  private readonly authService = inject(AuthService);
  private readonly authRouting = inject(AuthRoutingService);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);

  readonly serverError = signal<string | null>(null);
  readonly submitting = signal(false);
  readonly returnUrl = this.authRouting.safeReturnUrl(
    this.route.snapshot.queryParamMap.get('returnUrl'),
  );

  private readonly registerModel = signal<RegisterDTO>({
    email: '',
    password: '',
    confirmPassword: '',
  });

  readonly registerForm = form(this.registerModel, (schemaPath) => {
    authSchema(schemaPath);
    minLength(schemaPath.password, 8, {
      message: 'Password must be at least 8 characters long',
    });
    required(schemaPath.confirmPassword, { message: 'Please confirm your Password' });
    validate(schemaPath.confirmPassword, ({ value, valueOf }) => {
      if (!value() || value() === valueOf(schemaPath.password)) {
        return null;
      }

      return { kind: 'passwordMismatch', message: 'Passwords do not match' };
    });
  });

  onSubmit(event: Event): void {
    event.preventDefault();
    this.serverError.set(null);
    if (this.registerForm().invalid() || this.submitting()) {
      return;
    }

    const { email, password } = this.registerModel();
    this.register({ email, password });
  }

  register(credentials: AuthDTO): void {
    if (this.submitting()) {
      return;
    }

    this.submitting.set(true);
    this.authService.register(credentials).subscribe({
      next: () => {
        const destination = this.authRouting.destinationAfterAuth(
          this.route.snapshot.queryParamMap.get('returnUrl'),
        );
        void this.router.navigateByUrl(destination);
      },
      error: (error: HttpErrorResponse) => {
        this.serverError.set(
          error.error?.message ?? 'Unable to create your account. Please try again.',
        );
        this.submitting.set(false);
      },
    });
  }
}
