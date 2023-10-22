// Shortest Path
MATCH (root:Benutzer {name:"daniel.mayer"})

MATCH (root)-[:BIETET_AN]->()<-[:WUENSCHT]-(wunsch) // wunsch des Benutzers

MATCH path=shortestPath((root)-[*1..8]->(wunsch)) // maximal 3 tauschpartner (viereckstausch)
RETURN path