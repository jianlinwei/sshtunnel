/* sshtunnel - SSH Tunnel App for Android
 * Copyright (C) 2011 Max Lv <max.c.lv@gmail.com>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 * 
 * 
 *                            ___====-_  _-====___
 *                      _--^^^#####//      \\#####^^^--_
 *                   _-^##########// (    ) \\##########^-_
 *                  -############//  |\^^/|  \\############-
 *                _/############//   (@::@)   \\############\_
 *               /#############((     \\//     ))#############\
 *              -###############\\    (oo)    //###############-
 *             -#################\\  / VV \  //#################-
 *            -###################\\/      \//###################-
 *           _#/|##########/\######(   /\   )######/\##########|\#_
 *           |/ |#/\#/\#/\/  \#/\##\  |  |  /##/\#/  \/\#/\#/\#| \|
 *           `  |/  V  V  `   V  \#\| |  | |/#/  V   '  V  V  \|  '
 *              `   `  `      `   / | |  | | \   '      '  '   '
 *                               (  | |  | |  )
 *                              __\ | |  | | /__
 *                             (vvv(VVV)(VVV)vvv)
 *
 *                              HERE BE DRAGONS
 *
 */

package org.sshtunnel;

import org.sshtunnel.db.Profile;
import org.sshtunnel.db.ProfileFactory;
import org.sshtunnel.utils.Constraints;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.util.Log;

public class SSHTunnelReceiver {

	private static final String TAG = "SSHTunnelReceiver";

	public void onReceive(Context context, Intent intent, boolean enable) {

		Profile profile = ProfileFactory.getProfile(context);
		
		if (profile == null) {
			Log.e(TAG, "Exception when get preferences");
			return;
		}

		if (profile.isAutoConnect() || enable) {

			NotificationManager notificationManager = (NotificationManager) context
					.getSystemService(Context.NOTIFICATION_SERVICE);
			Notification notification = new Notification();
			intent = new Intent(context, SSHTunnel.class);
			intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
			PendingIntent pendIntent = PendingIntent.getActivity(context, 0, intent, 0);
			
			notification.icon = R.drawable.ic_stat;
			notification.tickerText = context.getString(R.string.auto_connecting);
			notification.flags = Notification.FLAG_ONGOING_EVENT;
			
			notification.setLatestEventInfo(context, context.getString(R.string.app_name),
					context.getString(R.string.auto_connecting), pendIntent);
			notificationManager.notify(1, notification);

			Intent it = new Intent(context, SSHTunnelService.class);
			Bundle bundle = new Bundle();
			bundle.putInt(Constraints.ID, profile.getId());

			it.putExtras(bundle);
			context.startService(it);
		}
	}

}
