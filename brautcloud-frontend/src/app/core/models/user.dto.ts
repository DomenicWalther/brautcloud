import { EventDto } from './event.dto';

export interface UserDto {
  createdAt: string;
  email: string;
  emailVerified: boolean;
  onboardingComplete: boolean;
  events: EventDto[];
  id: string;
}
