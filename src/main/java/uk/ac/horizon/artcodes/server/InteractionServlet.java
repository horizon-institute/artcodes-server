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

import uk.ac.horizon.aestheticodes.model.ExperienceCache;
import uk.ac.horizon.aestheticodes.model.ExperienceInteraction;
import uk.ac.horizon.artcodes.server.utils.ArtcodeServlet;
import uk.ac.horizon.artcodes.server.utils.DataStore;
import uk.ac.horizon.artcodes.server.utils.HTTPException;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.logging.Logger;

public class InteractionServlet extends ArtcodeServlet
{
	private static final Logger logger = Logger.getLogger(InteractionServlet.class.getName());

	@Override
	protected void doPost(HttpServletRequest request, HttpServletResponse response)
	{
		try
		{
			verifyApp(request);
			final String experienceID = request.getParameter("experience");

			logger.info(experienceID);

			ExperienceInteraction interaction = DataStore.load().type(ExperienceInteraction.class).id(experienceID).now();
			if (interaction == null)
			{
				interaction = new ExperienceInteraction(experienceID);
			}

			interaction.increment();

			DataStore.save().entity(interaction);
			DataStore.get().delete().type(ExperienceCache.class).id("popular");
		}
		catch (HTTPException e)
		{
			e.writeTo(response);
		}
	}
}
