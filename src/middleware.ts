import {clerkMiddleware, ClerkMiddlewareAuth, createRouteMatcher} from "@clerk/nextjs/server";
import {NextRequest} from "next/server";

const isProtectedRoute = createRouteMatcher([
  "/dashboard(.*),  /upload(.*)",
]);
export default clerkMiddleware((auth: ClerkMiddlewareAuth, req : NextRequest) => {
  if (isProtectedRoute(req)) auth().protect();
}, {debug: true});

export const config = {
  matcher: ["/((?!.*\\..*|_next).*)", "/", "/(api|trpc)(.*)"],
};
