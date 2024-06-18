"use client";

const alben = ["album1", "album2", "album3", "album4"];

export default function Dashboard() {
  return (
    <main className="container mx-auto mt-5 ">
      <h2 className="text-4xl font-bold mx-10 md:mx-0">Deine Alben</h2>
      <div className="grid grid-cols-1 gap-5 mx-10 md:mx-0 md:grid-cols-2 lg:grid-cols-3 mt-10">
        {alben.map((album) => {
          return (
            <div key={album} className="bg-gray-500 pb-5 ">
              <img
                src="https://via.placeholder.com/400x266"
                className="w-full"
              />
              <p className="text-white text-sm font-medium flex mt-5 justify-center">
                {album}
              </p>
            </div>
          );
        })}
      </div>
    </main>
  );
}
