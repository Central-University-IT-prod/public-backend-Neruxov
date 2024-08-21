from io import BytesIO
from flask import Flask, request, send_file
import staticmaps
import requests
import os

app = Flask(__name__)


@app.post("/render")
def render():
    data = request.json
    if "locations" not in data or "route" not in data:
        return "Invalid request", 400

    locations = data["locations"]
    for location in locations:
        if len(location) != 2 or not isinstance(location[0], (int, float)) or not isinstance(location[1], (int, float)):
            return "Invalid request", 400

    route = data["route"]
    for location in route:
        if len(location) != 2 or not isinstance(location[0], (int, float)) or not isinstance(location[1], (int, float)):
            return "Invalid request", 400

    if len(locations) < 2 or len(route) < 2:
        return "Invalid request", 400

    width = data.get("width", 1280)
    height = data.get("height", 720)

    locations_latlng = [staticmaps.create_latlng(lat, lon) for lon, lat in locations]
    route_latlng = [staticmaps.create_latlng(lat, lon) for lon, lat in route]

    context = staticmaps.Context()
    context.set_tile_provider(staticmaps.tile_provider_OSM)

    for latlng in locations_latlng:
        context.add_object(staticmaps.Marker(latlng, color=staticmaps.BLUE))

    context.add_object(staticmaps.Line(route_latlng, color=staticmaps.BLUE, width=3))

    image = context.render_pillow(width, height)

    image_io = BytesIO()
    image.save(image_io, "PNG")
    image_io.seek(0)

    return send_file(image_io, mimetype="image/png", as_attachment=True, download_name="map.png")


if __name__ == "__main__":
    app.run(port=os.environ.get("STATIC_MAP_SERVICE_PORT", 8081), host='0.0.0.0')
