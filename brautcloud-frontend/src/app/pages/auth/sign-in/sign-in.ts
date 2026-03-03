import { Component, inject, signal } from '@angular/core';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { AuthService } from '../../../services/auth-service';
import { EventService } from '../../../services/event-service';
import { form, FormField, submit } from '@angular/forms/signals';

interface LoginData {
  email: string;
  password: string;
}

@Component({
  selector: 'app-sign-in',
  imports: [RouterLink, FormField],
  templateUrl: './sign-in.html',
  styles: ``,
})
export class SignIn {
  private authService = inject(AuthService);
  private eventService = inject(EventService);
  private router = inject(Router);
  private route = inject(ActivatedRoute);

  loginModel = signal<LoginData>({
    email: '',
    password: '',
  });

  loginForm = form(this.loginModel);

  onSubmit(event: Event) {
    event.preventDefault();
    const { email, password } = this.loginModel();
    this.login({ email, password });
  }

  login({ email, password }: LoginData) {
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

  getEvents() {
    this.eventService.getEvents();
  }
}
