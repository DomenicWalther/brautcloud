import { Component, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { AuthService } from '../../../services/auth-service';
import { form, FormField, required, validate } from '@angular/forms/signals';
import { AuthDTO } from '../../../core/models/auth-dto';
import { authSchema } from '../schemas/auth.schema';

interface RegisterDTO {
  email: string;
  password: string;
  confirmPassword: string;
}

@Component({
  selector: 'app-sign-up',
  imports: [RouterLink, FormField],
  templateUrl: './sign-up.html',
  styles: ``,
})
export class SignUp {
  private authService = inject(AuthService);
  private serverError = signal<string | null>(null);

  registerModel = signal<RegisterDTO>({
    email: '',
    password: '',
    confirmPassword: '',
  });

  registerForm = form(this.registerModel, (schemaPath) => {
    authSchema(schemaPath, this.serverError);
    required(schemaPath.confirmPassword, { message: 'Please confirm your Password' });
    validate(schemaPath.confirmPassword, ({ value, valueOf }) => {
      if (value() !== valueOf(schemaPath.password)) {
        return { kind: 'passwordMismatch', message: 'Passwords do not match' };
      }
      return null;
    });
  });

  onSubmit(event: Event) {
    event.preventDefault();
    const { email, password } = this.registerModel();
    this.register({ email, password });
  }

  register({ email, password }: AuthDTO) {
    this.authService
      .register({
        email,
        password,
      })
      .subscribe((response: any) => {
        console.log(response);
      });
  }
}
