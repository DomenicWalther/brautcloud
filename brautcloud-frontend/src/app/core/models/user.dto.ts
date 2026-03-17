import { EventDto } from './event.dto';

export interface UserDto {
  createdAt: string;
  email: string;
  emailVerified: boolean;
  events: EventDto[];
  id: string;
  lastName: string;
}
