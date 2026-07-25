import { Component } from '@angular/core';
import { RouterLink } from '@angular/router';
import { AuthShell } from '../../../components/auth-shell/auth-shell';

@Component({
  selector: 'app-reset-password',
  imports: [RouterLink, AuthShell],
  templateUrl: './reset-password.html',
  styles: ``,
})
export class ResetPassword {}
