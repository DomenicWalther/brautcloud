export interface PublicEventDto {
  date: string | null;
  eventName: string;
  firstNameCoupleOne: string;
  firstNameCoupleTwo: string;
  id: string;
  location: string;
}

export interface EventUpdateDto {
  date: string;
  eventName: string;
  firstNameCoupleOne: string;
  firstNameCoupleTwo: string;
  location: string;
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
}
