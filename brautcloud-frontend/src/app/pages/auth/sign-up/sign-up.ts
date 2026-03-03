import { Component, inject, signal } from '@angular/core';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { AuthService } from '../../../services/auth-service';
import { form, FormField, submit } from '@angular/forms/signals';

interface RegisterData {
  email: string;
  password: string;
}

@Component({
  selector: 'app-sign-up',
  imports: [RouterLink, FormField],
  templateUrl: './sign-up.html',
  styles: ``,
})
export class SignUp {
  private authService = inject(AuthService);

  registerModel = signal<RegisterData>({
    email: '',
    password: '',
  });

  registerForm = form(this.registerModel);

  onSubmit(event: Event) {
    event.preventDefault();
    const { email, password } = this.registerModel();
    this.login({ email, password });
  }

  login({ email, password }: RegisterData) {
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
