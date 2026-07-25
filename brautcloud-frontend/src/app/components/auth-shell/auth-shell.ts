import { Component, input } from '@angular/core';
import { RouterLink } from '@angular/router';

@Component({
  selector: 'app-auth-shell',
  imports: [RouterLink],
  templateUrl: './auth-shell.html',
  styleUrl: './auth-shell.css',
})
export class AuthShell {
  readonly eyebrow = input.required<string>();
  readonly title = input.required<string>();
  readonly description = input.required<string>();
}
