export default function Page({ params }: { params: { slug: string } }) {
  return (
    <main className="container mx-auto mt-5 ">
      <h2 className="text-4xl font-bold mx-10 md:mx-0">
        Album von {params.slug}
      </h2>
      <h3 className="text-2xl font-medium mx-10 mt-20 md:mx-0">
        Einstellungen
      </h3>
      <p>Name vom Album</p>
      <p>QR-Code zum Album</p>
      <p>Löschen</p>
      <p>Gallerie aktivieren</p>
    </main>
  );
}
