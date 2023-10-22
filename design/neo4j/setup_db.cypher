// Personen
MERGE (dm:Benutzer { name: "daniel.mayer"})-[:BIETET_AN]->(dma:Angebot {
    semester: "2324",
    knz:3112,
    name:"softwaretechnik",
    typ:"praktikum",
    blacklist: ["max.muster", "mike.hunt", "henri.henrisson"]
})
MERGE (dm)-[:BIETET_AN]->(dma2:Angebot {
    semester: "2324",
    knz:3311,
    name:"IT-Recht und Datenschutz",
    typ:"übung",
    blacklist: []
})
MERGE (jm:Benutzer { name: "jonathan.mensch"})-[:BIETET_AN]->(jma:Angebot {
    semester: "2324",
    knz:3112,
    name:"softwaretechnik",
    typ:"praktikum",
    blacklist: ["lukas.tiger"]
})
MERGE (iw:Benutzer { name: "ina.weiß"})-[:BIETET_AN]->(iwa:Angebot {
    semester: "2324",
    knz:3112,
    name:"softwaretechnik",
    typ:"praktikum",
    blacklist: ["frank.dudel"]
})
MERGE (jk:Benutzer { name: "jana.klein"})-[:BIETET_AN]->(jka:Angebot {
    semester: "2324",
    knz:3112,
    name:"softwaretechnik",
    typ:"praktikum",
    blacklist: ["arnaka.dudel"]
})
MERGE (jk)-[:BIETET_AN]->(jka2:Angebot {
    semester: "2324",
    knz:3311,
    name:"IT-Recht und Datenschutz",
    typ:"übung",
    blacklist: []
})
MERGE (ra:Benutzer { name: "ringer.arnold"})-[:BIETET_AN]->(raa:Angebot {
    semester: "2324",
    knz:3112,
    name:"softwaretechnik",
    typ:"praktikum",
    blacklist: ["christian.ok"]
})
MERGE (tm:Benutzer { name: "toby.mcfly"})-[:BIETET_AN]->(tma:Angebot {
    semester: "2324",
    knz:3112,
    name:"softwaretechnik",
    typ:"praktikum",
    blacklist: []
})

// Module
MERGE (m2:Modul {
    knz:3311,
    name:"IT-Recht und Datenschutz",
    typ:"übung"
})

// Gruppen
MERGE (a:Gruppe {
    knz: "A",
    wochentag: 4,
    von: time("10:30"),
    bis: time("11:30"),
    leitung: "igler",
    raum: "UDE-C001"
})
MERGE (e:Gruppe {
    knz: "E",
    wochentag: 5,
    von: time("14:15"),
    bis: time("15:45"),
    leitung: "igler",
    raum: "UDE-C035"
})
MERGE (b:Gruppe {
    knz: "B",
    wochentag: 2,
    von: time("10:00"),
    bis: time("11:30"),
    leitung: "igler",
    raum: "UDE-C010"
})
MERGE (i:Gruppe {
    knz: "I",
    wochentag: 1,
    von: time("16:00"),
    bis: time("17:30"),
    leitung: "iglers frau",
    raum: "UDE-B002"
})
MERGE (k:Gruppe {
    knz: "K",
    wochentag: 5,
    von: time("14:15"),
    bis: time("15:45"),
    leitung: "iglers frau",
    raum: "UDE-C035"
})
MERGE (a2:Gruppe {
    knz: "A",
    wochentag: 5,
    von: time("14:15"),
    bis: time("15:45"),
    leitung: "erhard",
    raum: "UDE-B002"
})
MERGE (b2:Gruppe {
    knz: "B",
    wochentag: 2,
    von: time("10:15"),
    bis: time("11:45"),
    leitung: "erhard",
    raum: "UDE-B001"
})

// Relationen
MERGE (dma)-[:VERAEUSSERT]->(a)
MERGE (e)-[:WUENSCHT]->(dma)
MERGE (dma2)-[:VERAEUSSERT]->(a2)
MERGE (b2)-[:WUENSCHT]->(dma2)

MERGE (jma)-[:VERAEUSSERT]->(e)
MERGE (b)-[:WUENSCHT]->(jma)

MERGE (iwa)-[:VERAEUSSERT]->(b)
MERGE (i)-[:WUENSCHT]->(iwa)

MERGE (jka)-[:VERAEUSSERT]->(i)
MERGE (k)-[:WUENSCHT]->(jka)
MERGE (jka2)-[:VERAEUSSERT]->(b2)
MERGE (a2)-[:WUENSCHT]->(jka2)

MERGE (raa)-[:VERAEUSSERT]->(i)

MERGE (tma)-[:VERAEUSSERT]->(b)
MERGE (a)-[:WUENSCHT]->(tma)