import { EventDto } from './event.dto';

export interface UserDto {
  createdAt: string;
  email: string;
  emailVerified: boolean;
  events: EventDto[];
  firstNameCoupleOne: string;
  firstNameCoupleTwo: string;
  id: number;
  lastName: string;
}
