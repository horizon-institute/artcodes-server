/*
 * Artcodes recognises a different marker scheme that allows the
 * creation of aesthetically pleasing, even beautiful, codes.
 * Copyright (C) 2013-2015  The University of Nottingham
 *
 *     This program is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU Affero General Public License as published
 *     by the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 *
 *     This program is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU Affero General Public License for more details.
 *
 *     You should have received a copy of the GNU Affero General Public License
 *     along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package uk.ac.horizon.artcodes.server;

import uk.ac.horizon.aestheticodes.model.ExperienceAvailability;
import uk.ac.horizon.aestheticodes.model.ExperienceCache;
import uk.ac.horizon.aestheticodes.model.ExperienceInteraction;
import uk.ac.horizon.artcodes.server.utils.ArtcodeServlet;
import uk.ac.horizon.artcodes.server.utils.DataStore;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

public class RecommendedServlet extends ArtcodeServlet
{
	private static class Nearby
	{
		private final String uri;
		private final double distance;

		Nearby(String uri, double distance)
		{
			this.uri = uri;
			this.distance = distance;
		}
	}

	private static class LatLng
	{
		private static final double precision = 1000d;
		private final double latitude;
		private final double longitude;

		LatLng(double latitude, double longitude)
		{
			this.latitude = Math.round(latitude * precision) / precision;
			this.longitude = Math.round(longitude * precision) / precision;
		}

		@Override
		public String toString()
		{
			return "lat:" + latitude + ",lng:" + longitude;
		}
	}

	private static class Result
	{
		private final Set<String> ids = new HashSet<>();
		private final Map<String, List<String>> result = new HashMap<>();

		private Result()
		{
		}

		void add(String category, List<String> experiences)
		{
			final List<String> finalExperiences = new ArrayList<>();
			for (String experience : experiences)
			{
				if (!ids.contains(experience))
				{
					ids.add(experience);
					finalExperiences.add(experience);
				}
			}
			result.put(category, finalExperiences);
		}

		void write(HttpServletResponse response) throws IOException
		{
			writeJSON(response, result);
		}
	}

	private static final int NEARBY_COUNT = 4;
	private static final int POPULAR_COUNT = 6;
	private static final int nearbyDistance = 100;
	private static final Logger logger = Logger.getLogger(RecommendedServlet.class.getSimpleName());

	@Override
	public void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException
	{
		final Result result = new Result();
		result.add("nearby", getNearbyExperiences(getLocation(req)));
		result.add("featured", getFeaturedExperiences());
		result.add("popular", getPopularExperiences());
		resp.addHeader("Cache-Control", "max-age=60, stale-while-revalidate=604800");
		result.write(resp);
	}

	private LatLng getLocation(HttpServletRequest req)
	{
		try
		{
			if (req.getParameter("lat") != null && req.getParameter("lon") != null)
			{
				return new LatLng(Double.parseDouble(req.getParameter("lat")), Double.parseDouble(req.getParameter("lon")));
			}
//			else if (req.getHeader("X-AppEngine-CityLatLong") != null)
//			{
//				String[] positions = req.getHeader("X-AppEngine-CityLatLong").split(",");
//				return new LatLng(Double.parseDouble(positions[0]), Double.parseDouble(positions[1]));
//			}
		}
		catch (Exception e)
		{
			logger.log(Level.WARNING, "Error reading location", e);
		}
		return null;
	}

	private List<String> getFeaturedExperiences()
	{
		final ExperienceCache cache = DataStore.load().type(ExperienceCache.class).id("featured").now();
		if (cache != null)
		{
			return cache.getExperiences();
		}
		return Collections.emptyList();
	}

	private List<String> getNearbyExperiences(LatLng location)
	{
		if (location != null)
		{
			final ExperienceCache cache = DataStore.load().type(ExperienceCache.class).id(location.toString()).now();
			if (cache != null)
			{
				return cache.getExperiences();
			}

			final List<Nearby> nearby = new ArrayList<>();
			final List<ExperienceAvailability> availabilities = DataStore.load().type(ExperienceAvailability.class)
					.filter("lat >", null)
					.list();

			logger.info("Found " + availabilities.size() + " location results");
			for (ExperienceAvailability availability : availabilities)
			{
				if (availability.isActive())
				{
					try
					{
						final double distance = availability.getMilesFrom(location.latitude, location.longitude);
						if (distance < nearbyDistance)
						{
							nearby.add(new Nearby(availability.getUri(), distance));
						}
					}
					catch (Exception e)
					{
						logger.log(Level.WARNING, e.getMessage(), e);
					}
				}
			}
			logger.info("Found " + nearby.size() + " locations near to " + location);

			nearby.sort((o1, o2) -> (int) ((o1.distance - o2.distance) * 10000));

			final List<String> nearbyIDs = new ArrayList<>();
			for (Nearby nearbyID : nearby)
			{
				nearbyIDs.add(nearbyID.uri);
				if (nearbyIDs.size() >= NEARBY_COUNT)
				{
					break;
				}
			}

			final ExperienceCache nearbyCache = new ExperienceCache(location.toString(), nearbyIDs);
			DataStore.save().entity(nearbyCache);

			return nearbyIDs;
		}
		return Collections.emptyList();
	}

	private List<String> getPopularExperiences()
	{
		final ExperienceCache cache = DataStore.load().type(ExperienceCache.class).id("popular").now();
		if (cache != null)
		{
			return cache.getExperiences();
		}
		final List<ExperienceInteraction> interactions = DataStore.load().type(ExperienceInteraction.class)
				.filter("interactions >", 0)
				.order("-interactions")
				.limit(POPULAR_COUNT * 2)
				.list();

		final List<String> popularIDs = new ArrayList<>();
		for (ExperienceInteraction interaction : interactions)
		{
			if (interaction.getInteractions() > 0)
			{
				List<ExperienceAvailability> availabilities = DataStore.load()
						.type(ExperienceAvailability.class)
						.filter("uri", interaction.getUri())
						.list();
				if (availabilities.isEmpty())
				{
					popularIDs.add(interaction.getUri());
				}
				else
				{
					for (ExperienceAvailability availability : availabilities)
					{
						if (availability.isActive())
						{
							popularIDs.add(interaction.getUri());
							break;
						}
					}
				}
				if (popularIDs.size() >= POPULAR_COUNT)
				{
					break;
				}
			}
		}

		ExperienceCache popularCache = new ExperienceCache("popular", popularIDs);
		DataStore.save().entity(popularCache);
		return popularIDs;
	}
}
