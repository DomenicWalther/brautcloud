import { pgTable, serial, uuid } from "drizzle-orm/pg-core";
import { users } from "./users";

export const albums = pgTable("albums", {
  id: uuid("id").primaryKey(),
  userID: serial("user_id").references(() => users.id),
});
