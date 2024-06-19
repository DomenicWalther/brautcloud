import {SignedIn, SignedOut, SignInButton, UserButton} from "@clerk/nextjs";
import { Disclosure, Menu, Transition } from "@headlessui/react";
import { PlusSmallIcon } from "@heroicons/react/20/solid";
import { BellIcon } from "@heroicons/react/24/outline";
import { Fragment } from "react";

function classNames(...classes: string[]) {
  return classes.filter(Boolean).join(" ");
}

const navigation = [{ name: "Dashboard", href: "/dashboard", current: true }];

export const Navigation = () => {
  return (
    <nav className="w-full bg-gray-800 py-5">
      <div className="flex justify-between container mx-auto">
        <ul className="text-white flex items-center ">
          <li className="bg-gray-900 px-3 py-2 rounded-md text-sm font-medium">
            Dashboard
          </li>
        </ul>

        <div className="flex items-center gap-5">
          <button
            type="button"
            className="relative inline-flex items-center px-4 py-2 border border-transparent shadow-sm text-sm font-medium rounded-md text-white bg-indigo-500 hover:bg-indigo-600 focus:outline-none focus:ring-2 focus:ring-offset-2 focus:ring-offset-gray-800 focus:ring-indigo-500"
          >
            <PlusSmallIcon className="-ml-1 mr-2 h-5 w-5" aria-hidden="true" />
            <span>Neues Album</span>
          </button>
          <SignedIn>
            <UserButton />
          </SignedIn>
          <SignedOut>
              <SignInButton />
            </SignedOut>
        </div>
      </div>
    </nav>
  );
};
