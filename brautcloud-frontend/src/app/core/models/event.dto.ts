export interface PublicEventDto {
  date: string | null;
  eventName: string;
  firstNameCoupleOne: string;
  firstNameCoupleTwo: string;
  id: string;
  location: string;
  passwordProtected: boolean;
}

export interface EventUpdateDto {
  date: string;
  eventName: string;
  firstNameCoupleOne: string;
  firstNameCoupleTwo: string;
  location: string;
  password?: string;
}

export interface EventDto {
  date: string | null;
  eventName: string;
  firstNameCoupleOne: string;
  firstNameCoupleTwo: string;
  id: string;
  location: string;
  userId: string;
  viewCount: number;
  guestCount: number;
  hasPassword: boolean;
}
