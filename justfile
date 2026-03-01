backend *args:
  cd brautcloud-backend && just {{args}}

frontend *args:
  cd brautcloud-frontend && just {{args}}

dev:
  just backend dev & just frontend dev
