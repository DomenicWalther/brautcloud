backend *args:
  cd brautcloud-backend && just {{args}}

frontend *args:
  cd brautcloud-frontend && just {{args}}

dev:
  just backend dev & just frontend dev

dev-front:
  just backend dev-full & just frontend dev
