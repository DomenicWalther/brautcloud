import { Component, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { AuthService } from '../../../services/auth-service';
import { form, FormField } from '@angular/forms/signals';
import { AuthDTO } from '../../../core/models/auth-dto';

@Component({
  selector: 'app-sign-up',
  imports: [RouterLink, FormField],
  templateUrl: './sign-up.html',
  styles: ``,
})
export class SignUp {
  private authService = inject(AuthService);

  registerModel = signal<AuthDTO>({
    email: '',
    password: '',
  });

  registerForm = form(this.registerModel);

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
