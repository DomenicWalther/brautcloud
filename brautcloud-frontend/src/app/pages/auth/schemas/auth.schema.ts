import { email, required, SchemaPathTree, validate } from '@angular/forms/signals';
import { AuthDTO } from '../../../core/models/auth.dto';

export function authSchema<T extends AuthDTO>(schemaPath: SchemaPathTree<T>) {
  required(schemaPath.email, { message: 'Email is required' });
  email(schemaPath.email, { message: 'Email is invalid' });
  validate(schemaPath.email, ({ value }) =>
    value().length <= 254
      ? null
      : { kind: 'emailTooLong', message: 'Email must be 254 characters or shorter' },
  );
  required(schemaPath.password, { message: 'Password is required' });
  validate(schemaPath.password, ({ value }) =>
    value().length <= 72
      ? null
      : { kind: 'passwordTooLong', message: 'Password must be 72 characters or shorter' },
  );
}
