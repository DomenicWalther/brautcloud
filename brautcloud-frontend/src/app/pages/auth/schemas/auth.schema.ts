import { email, required, SchemaPathTree, validate } from '@angular/forms/signals';
import { AuthDTO } from '../../../core/models/auth-dto';
import { Signal } from '@angular/core';

export function authSchema<T extends AuthDTO>(
  schemaPath: SchemaPathTree<T>,
  serverError: Signal<string | null>,
) {
  required(schemaPath.email, { message: 'Email is required' });
  email(schemaPath.email, { message: 'Email is invalid' });
  required(schemaPath.password, { message: 'Password is required' });
  validate(schemaPath.password, () => {
    const error = serverError();
    if (error) {
      return { kind: 'serverError', message: error };
    }

    return null;
  });
}
