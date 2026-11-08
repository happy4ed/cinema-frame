"""Refresh film metadata for the next APK build; never downloads poster files."""
import argparse
import csv
import datetime
import gzip
import io
import json
from pathlib import Path
import urllib.request
import urllib.parse


def rows(dataset):
    request = urllib.request.Request(
        f"https://datasets.imdbws.com/{dataset}.tsv.gz",
        headers={"User-Agent": "CinemaFramePersonal/0.3 metadata-builder"},
    )
    with urllib.request.urlopen(request, timeout=120) as response:
        with gzip.GzipFile(fileobj=response) as compressed:
            yield from csv.DictReader(io.TextIOWrapper(compressed, encoding="utf-8"), delimiter="\t")


def map_countries(movies, raw_path=None):
    """Wikidata P345 -> direct P495 -> ISO P297. Never merge HK/TW into CN."""
    query = 'SELECT DISTINCT ?imdb ?iso WHERE { ?film wdt:P345 ?imdb; wdt:P495 ?country. ?country wdt:P297 ?iso. }'
    if raw_path:
        raw = Path(raw_path).read_text(encoding="utf-8")
    else:
        bindings = []
        for start in range(0, len(movies), 200):
            ids = " ".join(json.dumps(m["id"]) for m in movies[start:start+200])
            batch_query = 'SELECT DISTINCT ?imdb ?iso WHERE { VALUES ?imdb { ' + ids + ' } ?film wdt:P345 ?imdb; wdt:P495 ?country. ?country wdt:P297 ?iso. }'
            url = "https://query.wikidata.org/sparql?" + urllib.parse.urlencode({"query": batch_query, "format": "json"})
            req = urllib.request.Request(url, headers={"User-Agent": "CinemaFrame/0.5 country metadata", "Accept": "application/sparql-results+json"})
            with urllib.request.urlopen(req, timeout=45) as response:
                batch = json.loads(response.read().decode("utf-8"))
            bindings.extend(batch["results"]["bindings"])
            print(f"Wikidata {min(start+200,len(movies))}/{len(movies)}", flush=True)
        raw = json.dumps({"results": {"bindings": bindings}})
    bindings = json.loads(raw)["results"]["bindings"]
    if not bindings:
        raise RuntimeError("Empty Wikidata response; existing catalogue was not replaced")
    mapping = {}
    for row in bindings:
        mapping.setdefault(row["imdb"]["value"], set()).add(row["iso"]["value"])
    for movie in movies:
        movie["countries"] = sorted(mapping.get(movie["id"], set()))
    found = sum(bool(m["countries"]) for m in movies)
    return {"complete": True, "source": "Wikidata P495/P345/P297", "fetched_at": datetime.datetime.now(datetime.timezone.utc).isoformat(), "total": len(movies), "mapped": found, "unknown": len(movies)-found}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--min-rating", type=float, default=6)
    parser.add_argument("--min-votes", type=int, default=10000)
    parser.add_argument("--countries-only", action="store_true")
    parser.add_argument("--wikidata-response", help="Saved actual Wikidata JSON response (not fixture data)")
    args = parser.parse_args()
    if args.countries_only:
        project = Path(__file__).resolve().parents[1]
        payload = json.loads((project / "android/assets/movies.json").read_text(encoding="utf-8"))
        payload["country_mapping"] = map_countries(payload["movies"], args.wikidata_response)
        for name in ("android/assets/movies.json", "data/movies.json"):
            target = project/name; target.parent.mkdir(parents=True, exist_ok=True)
            temp = target.with_suffix(".json.tmp"); temp.write_text(json.dumps(payload, ensure_ascii=False, separators=(",", ":")), encoding="utf-8"); temp.replace(target)
        print(json.dumps(payload["country_mapping"]))
        return
    ratings = {}
    for row in rows("title.ratings"):
        rating, votes = float(row["averageRating"]), int(row["numVotes"])
        if rating >= args.min_rating and votes >= args.min_votes:
            ratings[row["tconst"]] = (rating, votes)
    movies = []
    for row in rows("title.basics"):
        if row["tconst"] not in ratings or row["titleType"] != "movie" or row["isAdult"] != "0" or row["startYear"] == "\\N":
            continue
        rating, votes = ratings[row["tconst"]]
        movies.append({
            "id": row["tconst"], "title": row["primaryTitle"],
            "originalTitle": row["originalTitle"], "year": int(row["startYear"]),
            "rating": rating, "votes": votes,
            "genres": [] if row["genres"] == "\\N" else row["genres"].split(","),
        })
    if not movies:
        raise RuntimeError("No movies received; existing catalogue was not replaced")
    country_mapping = map_countries(movies, args.wikidata_response)
    print(json.dumps(country_mapping))
    movies.sort(key=lambda movie: (-movie["votes"], movie["id"]))
    payload = json.dumps({
        "source": "https://datasets.imdbws.com/",
        "updated": datetime.datetime.now(datetime.timezone.utc).date().isoformat(),
        "movies": movies, "country_mapping": country_mapping,
    }, ensure_ascii=False, separators=(",", ":"))
    project = Path(__file__).resolve().parents[1]
    for relative in ("data/movies.json", "android/assets/movies.json"):
        target = project / relative
        target.parent.mkdir(parents=True, exist_ok=True)
        temporary = target.with_suffix(".json.tmp")
        temporary.write_text(payload, encoding="utf-8")
        temporary.replace(target)
    print(json.dumps({"indexed": len(movies), "default_eligible": sum(movie["rating"] >= 7 and movie["votes"] >= 50000 for movie in movies)}))


if __name__ == "__main__":
    main()
