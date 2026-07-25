export interface AuthDTO {
  email: string;
  password: string;
}

export interface AuthResponse {
  accessToken: string;
  onboardingComplete: boolean;
}
