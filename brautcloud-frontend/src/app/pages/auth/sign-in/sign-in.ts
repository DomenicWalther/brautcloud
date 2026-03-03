import { Component, inject } from '@angular/core';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { AuthService } from '../../../services/auth-service';
import { EventService } from '../../../services/event-service';

@Component({
  selector: 'app-sign-in',
  imports: [RouterLink],
  templateUrl: './sign-in.html',
  styles: ``,
})
export class SignIn {
  private authService = inject(AuthService);
  private eventService = inject(EventService);
  private router = inject(Router);
  private route = inject(ActivatedRoute);

  login() {
    this.authService.login().subscribe({
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
