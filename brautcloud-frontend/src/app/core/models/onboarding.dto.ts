import { EventDto } from './event.dto';

export interface OnboardingDto {
  firstName: string;
  partnerFirstName: string;
  familyName: string;
  venue: string;
}

export interface OnboardingResponse {
  onboardingComplete: true;
  event: EventDto | null;
}
