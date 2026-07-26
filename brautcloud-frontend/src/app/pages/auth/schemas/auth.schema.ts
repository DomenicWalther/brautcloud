import { email, required, SchemaPathTree } from '@angular/forms/signals';
import { AuthDTO } from '../../../core/models/auth.dto';

export function authSchema<T extends AuthDTO>(schemaPath: SchemaPathTree<T>) {
  required(schemaPath.email, { message: 'Email is required' });
  email(schemaPath.email, { message: 'Email is invalid' });
  required(schemaPath.password, { message: 'Password is required' });
}
