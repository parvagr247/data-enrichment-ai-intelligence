export interface UserDto {
  id: string;
  name: string;
  email: string;
  createdAt: string;
}

export interface AuthResponse {
  token: string;
  user: UserDto;
}

export interface LoginRequest {
  email: string;
  password: string;
}

export interface RegisterRequest {
  name: string;
  email: string;
  password: string;
}
