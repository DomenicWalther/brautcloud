import { Component, inject, signal } from '@angular/core';
import { RouterOutlet } from '@angular/router';
import { AuthService } from './services/auth-service';

@Component({
  selector: 'app-root',
  imports: [RouterOutlet],
  templateUrl: './app.html',
  styleUrl: './app.css',
})
export class App {
  protected readonly title = signal('brautcloud-frontend');
  private authService = inject(AuthService);

  ngOnInit() {
    this.authService.refresh(
      (token) => console.log('Session restored'),
      () => console.log('Session expired'),
    );
  }
}
