// Copyright (c) 2017 Google Inc.
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
// http://www.apache.org/licenses/LICENSE-2.0
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

// Cloud Functions for Firebase is used to process session reservation requests.
//
// Clients are permitted to write requests into the queue. RTDB Rules are used
// to restrict clients to writing one request at a time.
//
// Each written request triggers a function that processes the request. Once the
// request is processed it is removed from the queue allowing clients to submit
// more requests.

var functions = require('firebase-functions');
var google = require('googleapis');

// firebase-admin module is used to perform RTDB updates while processing
// reservation requests.
const admin = require('firebase-admin');
admin.initializeApp(functions.config().firebase);

// Amount of time (in millis) before the start of a session required for
// reservations to be allowed. 30 minutes.
const RES_CUT_OFF = 1800000;

const PATH_SESSIONS = 'sessions';
const PATH_RESERVATIONS = 'reservations';
const PATH_RESULTS = 'results';
const PATH_STATUS = 'status';
const PATH_SEATS = 'seats';
const PATH_ACTION = 'action';
const PATH_SESSION = 'session';
const PATH_QUEUE = 'queue';
const PATH_PROMO_QUEUE = 'promo_queue';
const PATH_LAST_STATUS_CHANGED = 'last_status_changed';
const PATH_EVENTS = 'events';

const REQUEST_ID = 'request_id';

const ACTION_RESERVE = 'reserve';
const ACTION_RETURN = 'return';

const STATUS_GRANTED = 'granted';
const STATUS_WAITING = 'waiting';
const STATUS_RETURNED = 'returned';

const RESERVATION_RESERVED = 'reserved';
const RESERVATION_DENIED_NO_SPACE = 'denied no space';
const RESERVATION_CLOSED = 'reservations closed';
const RESERVATION_RETURNED = 'returned';
const RESERVATION_RETURN_FAILED = 'unable to complete return';
const RESERVATION_FAILED = 'unable to complete reservation';
const RESERVATION_DENIED_CLASHING = 'denied other session clashes';

const RESULT_RESERVED = 'reserved';
const RESULT_RESERVED_NO_SPACE = 'reserve_denied_space';
const RESULT_RESERVED_CUTOFF = 'reserve_denied_cutoff';
const RESULT_RESERVED_CLASH = 'reserve_denied_clash';
const RESULT_RESERVED_FAILED = 'reserve_failed';

const RESULT_RETURNED = 'returned';
const RESULT_RETURNED_CUTOFF = 'return_denied_cutoff';
const RESULT_RETURNED_FAILED = 'return_failed';

const USERDATA_DISCOVERY_URL = 'USERDATA_DISCOVERY_URL';

const GOOGLE_PROVIDER_ID = 'google.com';

/**
 * Function to process "promotion from waitlist" requests. When a granted seat
 * is returned and there are attendees on the waitlist a request is written
 * to the promo_queue path.
 *
 * This function assumes that request are written in the form:
 * /promo_queue/<session id>/<timestamp>: true
 */
exports.processPromotions = functions.database.ref('/promo_queue/{sid}/{rid}').onWrite(event => {
  if (!event.data || !event.data.val() || !event.eventId) {
    return;
  }

  let eid = event.eventId;
  // Event IDs contain slashes and when written to the DB they cause children
  // using replace here to use dashes instead of slashes.
  eid = eid.replace(/\//g, "-");

  return isNewEvent(eid).then(function(newEvent) {
    if (newEvent) {
      const sid = event.params.sid;
      const rid = event.params.rid;
      return promoteFromWaitlist(sid).then(function (result) {
        console.log('attendee was promoted from waitlist of session ' + sid
            + ' ended with result: ' + result);
        return getPromoQueueReference(sid).child(rid).set({});
      }).catch(function (error) {
        console.error(error);
        return getPromoQueueReference(sid).child(rid).set({});
      });
    } else {
      // Do nothing, this is a duplicate event.
      console.log("duplicate event found: " + eid);
    }
  });
});

/**
 * This function listens to the request queue. When a request is written to the
 * queue by a client, this function executes.
 *
 * This function assumes that requests are submitted of the form:
 * /queue/<user id> : {
 *   session_id: <session id>,
 *   action: <reserve or return>,
 *   request_id: <timestamp>
 * }
 */
exports.processRequest = functions.database.ref('/queue/{uid}').onWrite(event => {
  if (!event.data || !event.data.val() || !event.eventId) {
    return;
  }

  let eid = event.eventId;
  // Event IDs contain slashes and when written to the DB they cause children
  // using replace here to use dashes instead of slashes.
  eid = eid.replace(/\//g, "-");

  return isNewEvent(eid).then(function(newEvent) {
    if (newEvent) {
      const request = event.data.val();
      const action = request[PATH_ACTION];
      const sid = request[PATH_SESSION];
      const rid = request[REQUEST_ID];
      const uid = event.params.uid;

      return process(uid, sid, rid, action).then(function(result) {
        console.log(action + ' with uid: ' + uid + ' and sid: ' + sid
            + ' ended with result: ' + result);
        return getQueueReference(uid).set({});
      }).catch(function(error) {
        console.error(error);
        return getQueueReference(uid).set({});
      });
    } else {
      // Do nothing, this is a duplicate event.
      console.log("duplicate event found: " + eid);
    }
  });
});

/**
 * Determine if an event is new.
 *
 * @param eid ID used to check for event's existance.
 * @returns {Promise.<TResult>} result and be true or false.
 */
function isNewEvent(eid) {
  let newEvent = false;

  // Start a transaction to check if the current event has already started.
  return admin.database().ref(PATH_EVENTS).child(eid).transaction(function(reqEvent) {
    // If the event does not exist write it to RTDB.
    if (!reqEvent) {
      reqEvent = true;
      newEvent = true;
    } else {
      newEvent = false;
    }
    return reqEvent;
  }).then(() => {
    return newEvent;
  });
}

/**
 * Process action (reserve or return) request by user with ID uid for session
 * with ID sid.
 *
 * @param uid ID of user requesting action (reserve or return).
 * @param sid Session ID of requested action (reserve or return).
 * @param rid ID of the reservation request.
 * @param action Intent of the request, to reserve or return reservation.
 * @returns {Promise.<TResult>} result of processing action.
 */
function process(uid, sid, rid, action) {
  // Get session.
  return getSessionReference(sid).once('value')
      .then(function(snapshot) {
    return snapshot.val();
  }).then(function(curr_session) {
    // Check that are reservations still open.
    const now = new Date();
    if (curr_session.time_start - RES_CUT_OFF <= now.getTime()) {
      return handleCutoff(uid, sid, rid, action);
    }

    if (action == ACTION_RESERVE) {
      return processReserve(uid, sid, rid, curr_session);
    } else if (action == ACTION_RETURN) {
      return processReturn(uid, sid, rid);
    }
  });
}

/**
 * Process a reservation request made by user with ID uid for a seat in session
 * with ID sid.
 *
 * @param uid ID of user requesting reservation.
 * @param sid Session ID of requested reservation.
 * @param rid ID of the reservation request.
 * @param curr_session Session body of requested reservation.
 * @returns {Promise.<TResult>} result of processing reservation.
 */
function processReserve(uid, sid, rid, curr_session) {
  return checkForClash(uid, curr_session).then(function(clash) {
    if (clash) {
      // Clash with other session so reject.
      return handleClash(uid, sid, rid);
    } else {
      // No clash so proceed with reservation request.
      return handleReservation(uid, sid, rid);
    }
  });
}

/**
 * Process a return request of an existing granted reservation
 * or waitlist reservation for user with ID uid for a session
 * with ID sid.
 *
 * @param uid ID of user requesting return.
 * @param sid Session ID of requested return.
 * @param rid ID of the reservation request.
 * @returns {Promise.<TResult>} result of processing return.
 */
function processReturn(uid, sid, rid) {
  return getReservationStatusReference(uid, sid).once('value')
      .then(function(snapshot) {
    const currentStatus = snapshot.val();
    if (currentStatus == STATUS_GRANTED) {
      return handleReturn(uid, sid, rid);
    } else if(currentStatus == STATUS_WAITING) {
      return handleRemove(uid, sid, rid);
    } else {
      return getReservationResultReference(uid, sid, rid).set(RESULT_RETURNED_FAILED)
          .then(function() {
            return RESERVATION_RETURN_FAILED;
          });
    }
  });
}

/**
 * Check whether or not the user currently has any reservations that would
 * clash with the time of the session in the current reservation request.
 * Current reservations include being on the waitlist for a session.
 *
 * @param uid ID of user requesting reservation.
 * @param curr_session Session trying to be reserved.
 * @returns {Promise.<TResult>} result can be true or false.
 */
function checkForClash(uid, curr_session) {
  // Get all existing reservations for this user
  return admin.database().ref(PATH_SESSIONS)
      .orderByChild(PATH_RESERVATIONS + '/' + uid + '/' + PATH_STATUS)
      .equalTo(STATUS_GRANTED).once('value').then(function(snapshot) {
        const sessions = snapshot.val();
        for (var temp_session_id in sessions) {
          // Check for clash with existing reservation
          if (curr_session.time_start < sessions[temp_session_id].time_end &&
              curr_session.time_end > sessions[temp_session_id].time_start) {
            return true;
          }
        }
        // Get all existing wait lists for this user
        return admin.database().ref(PATH_SESSIONS)
            .orderByChild(PATH_RESERVATIONS + '/' + uid + '/' + PATH_STATUS)
            .equalTo(STATUS_WAITING).once('value').then(function(snapshot) {
                const sessions = snapshot.val();
                for (var temp_session_id in sessions) {
                    // Check for clash with existing waitlist
                    if (curr_session.time_start < sessions[temp_session_id].time_end &&
                        curr_session.time_end > sessions[temp_session_id].time_start) {
                        return true;
                    }
                }
                return false;
            });
      });
}

/**
 * Handle any requested action that is made after the reservation cutoff time.
 * Reservation alterations are only allowed RES_CUT_OFF millis before a session
 * starts.
 *
 * @param uid ID of user requesting action (reserve or return).
 * @param sid Session ID of requested action (reserve or return).
 * @param rid ID of the reservation request.
 * @param action String defining request type, reserve or return.
 * @returns {Promise.<TResult>} string result indicating reservations are closed.
 */
function handleCutoff(uid, sid, rid, action) {
  if (action == ACTION_RESERVE) {
    return getReservationResultReference(uid, sid, rid).set(RESULT_RESERVED_CUTOFF)
        .then(function() {
          // Reservations are closed.
          return RESERVATION_CLOSED;
        });
  } else if (action == ACTION_RETURN) {
    return getReservationResultReference(uid, sid, rid).set(RESULT_RETURNED_CUTOFF)
        .then(function() {
          // Reservations are closed.
          return RESERVATION_CLOSED;
        });
  } else {
    return Promise.resolve(RESERVATION_CLOSED);
  }
}

/**
 * Handle a return request. If a user requests a reservation for a session that
 * has already been granted, that reservation should be returned.
 *
 * @param uid ID of user returning reservation.
 * @param sid Session ID of return request.
 * @param rid ID of the reservation request.
 * @returns {Promise.<TResult>} string result of return.
 */
function handleReturn(uid, sid, rid) {
  // Remove session
  return getSeatsReference(sid).transaction(function(seats) {
    if (seats) {
      seats.reserved--;
      setSeatAvailability(seats);
    }
    return seats;
  }).then(function(result) {
    var committed = result.committed;
    var snapshot = result.snapshot;
    if (committed && snapshot != null) {
      // Set status to returned
      return getReservationStatusReference(uid, sid).set(STATUS_RETURNED)
          .then(function() {
            // Set last status change time
            return getReservationLastStatusChangeReference(uid, sid).set(new Date().getTime())
          })
          .then(function() {
            // Set the request result
            return getReservationResultReference(uid, sid, rid).set(
                RESULT_RETURNED)
          })
          .then(function() {
            var waitlisted = snapshot.val().waitlisted;
            if (waitlisted) {
              // Initiate promotion from waitlist by writing to the promo_queue.
              // A function will process all promotion requests from the
              // promo_queue.
              return getPromoQueueReference(sid).child(new Date().getTime()).set(true);
            }
          })
          .then(function() {
            // Push reservation update to App Server.
            return sendReturn(uid, sid);
          })
          .then(function() {
            return RESERVATION_RETURNED;
          });
    } else {
      return getReservationResultReference(uid, sid, rid)
          .set(RESULT_RETURNED_FAILED).then(function() {
            return RESERVATION_RETURN_FAILED;
          });
    }
  });
}

/**
 * Handles the return of a waitlist reservation. This does not affect
 * the state of available seating. However if the user does return to
 * the waitlist they will have lost their position.
 *
 * @param uid ID of user returning waitlist reservation.
 * @param sid Session ID of returned waitlist entry.
 * @param rid ID of the reservation request.
 * @returns {Promise.<TResult>} string result of return.
 */
function handleRemove(uid, sid, rid) {
  return getReservationReference(uid, sid).transaction(function(reservation) {
    if (reservation) {
      if (reservation.status == STATUS_WAITING) {
        reservation.status = STATUS_RETURNED;
        reservation.last_status_changed = new Date().getTime();
        reservation.results[rid] = RESULT_RETURNED;
      }
    }
    return reservation;
  }).then(function(result) {
    if (result.committed && postReservation.status == STATUS_RETURNED) {
      return sendReturn(uid, sid).then(function() {
        return RESERVATION_RETURNED;
      });
    } else {
      return RESERVATION_RETURN_FAILED;
    }
  });
}

/**
 * Promote the user that is waiting the longest from the waitlist (if there is one)
 * to being granted a seat in the session.
 *
 * @param sid ID of session where a user will be promoted from the waitlist.
 */
function promoteFromWaitlist(sid) {
  // User that has been waiting the longest.
  var firstInLine;
  var prevUserStatus;
  return getSessionReference(sid).transaction(function(session) {
    firstInLine = null;
    prevUserStatus = null;
    if (session) {
      var smallestTime;
      var reservations = session.reservations;
      for (var uid in reservations) {
        if (reservations[uid].status == STATUS_WAITING) {
          if (!smallestTime || reservations[uid].last_status_changed < smallestTime) {
            firstInLine = uid;
            smallestTime = reservations[uid].last_status_changed;
          }
        }
      }

      // Check if anyone was in the line waiting for a seat.
      if (firstInLine) {
        // Keep track of the user's pervious status
        prevUserStatus = reservations[firstInLine].status;
        var seats = session.seats;
        // If there is a seat available give it to user first in line.
        if (seats.capacity > seats.reserved) {
          seats.reserved++;
          reservations[firstInLine].status = STATUS_GRANTED;
          reservations[firstInLine].last_status_changed = new Date().getTime();
          setSeatAvailability(seats);
        }
      } else {
        // If there is no one in the waitlist set waitlisted to false.
        session.seats.waitlisted = false;
      }
    }
    return session;
  }).then(function(result) {
    var committed = result.committed;
    var snapshot = result.snapshot.val();
    if (committed && snapshot != null) {
      // it is possible that the commit was successful but the promotion was not successful
      // compare the status of the user
      if (firstInLine) {
        // we have a waiting user selected
        // check if the user was promoted
        if (prevUserStatus == STATUS_WAITING &&
            snapshot.reservations[firstInLine].status == STATUS_GRANTED) {
          // user was promoted
          // Push reservation update to App Server.
          return sendReserve(firstInLine, sid)
              .then(function() {
                console.log('promoted user ' + firstInLine);
                console.log('send notification to user ' + firstInLine);
                // TODO(arthurthompson): send notification to promoted user.
              }).catch(function(err) {
                console.error(err);
                console.log('Unable to send promotion.');
              });
        } else {
          console.log(firstInLine + ' was first in waitlist but not promoted.');
        }
      } else {
        console.log('waitlist was empty for session ' + sid + ' so no promotions needed.');
      }
    }
  });
}

/**
 * Set the availability state of seats in a session.
 *
 * @param seats Object representing seats of a session.
 */
function setSeatAvailability(seats) {
  if (seats.capacity > seats.reserved) {
    seats.seats_available = true;
  } else {
    seats.seats_available = false;
  }
}

/**
 * Handle a clashing reservation request. When a user requests a reservation
 * for a session that clashes with an existing reservation of the user this
 * reservation is denied.
 *
 * The result of the denied reservation is written to:
 * /sessions/<session id>/reservations/<user id>: <result>
 *
 * @param uid ID of user requesting reservation.
 * @param sid Session ID of requested reservation.
 * @param rid ID of the reservation request.
 * @returns {Promise.<TResult>} string result of denied reservation.
 */
function handleClash(uid, sid, rid) {
  return getReservationResultReference(uid, sid, rid).set(RESULT_RESERVED_CLASH)
      .then(function() {
        return RESERVATION_DENIED_CLASHING;
      })
}

/**
 * Handle valid reservation request. A reservation request is considered valid
 * if there requesting user has no existing clashing reservations. Reservations
 * will be granted if there are available seats in the session.
 *
 * The result of the reservation is written to:
 * /sessions/<session id>/reservations/<user id>: <result>
 *
 * @param uid ID of user requesting reservation.
 * @param sid Session ID of requested reservation.
 * @param rid ID of the reservation request.
 * @returns {Promise.<TResult>} string result of handled reservation.
 */
function handleReservation(uid, sid, rid) {
  var prevSeats;
  return getSeatsReference(sid).transaction(function(seats) {
    prevSeats = {};
    if (seats) {
      prevSeats.reserved = seats.reserved;
      // Add reservation if possible
      if (seats.capacity > seats.reserved) {
        console.log(uid + ' has been granted a seat');
        seats.reserved++;
        setSeatAvailability(seats);
      } else {
        // If there are no seats available, set the session to waitlisted.
        // Any granted seat that is returned on a waitlisted session will
        // trigger a promotion from the waitlist to fill the returned seat.
        seats.waitlisted = true;
        console.log('no seats available');
      }
    }
    return seats;
  }).then(function(result) {
    var committed = result.committed;
    var snapshot = result.snapshot;
    if (committed && snapshot != null) {
      var currSeats = snapshot.val();
      if (currSeats.reserved - 1 == prevSeats.reserved) {
        // Set status to granted
        return getReservationStatusReference(uid, sid).set(STATUS_GRANTED)
            .then(function() {
              // Update last status changed value to now
              return getReservationLastStatusChangeReference(uid, sid).set(
                  new Date().getTime());
            })
            .then(function() {
              // Set the request result to reserved
              return getReservationResultReference(uid, sid, rid).set(
                  RESULT_RESERVED)
            })
            .then(function() {
              // Push reservation update to App Server.
              return sendReserve(uid, sid);
            })
            .then(function() {
              return RESERVATION_RESERVED;
            });
      } else {
        // Set status to waiting, put user on the waitlist
        return getReservationStatusReference(uid, sid).set(STATUS_WAITING)
            .then(function() {
              // Set last change status to now
              return getReservationLastStatusChangeReference(uid, sid).set(
                  new Date().getTime());
            })
            .then(function() {
              // Set request result to no space
              return getReservationResultReference(uid, sid, rid).set(
                  RESULT_RESERVED_NO_SPACE);
            })
            .then(function() {
              // Push reservation update to App Server.
              return sendWaitlist(uid, sid);
            })
            .then(function() {
              return RESERVATION_DENIED_NO_SPACE;
            });
      }
    } else {
      return getReservationResultReference(uid, sid, rid)
          .set(RESULT_RESERVED_FAILED).then(function() {
            return RESERVATION_FAILED;
          });
    }
  });
}

/**
 * Send reserve session update to App Server. This allows other client
 * devices to sync the state of reservations.
 *
 * @param uid ID of user reserving a seat in a session.
 * @param sid ID of session being reserved.
 * @returns {Promise.<TResult>} empty result.
 */
function sendReserve(uid, sid) {
  var jwtClient = getJwtClient();

  return jwtClient.authorize(function (err, tokens) {
    if (err) {
      console.log('Unable to authorize ' + err);
      return;
    }

    return getProviderId(uid).then(function(providerId) {
      return google.discoverAPI(USERDATA_DISCOVERY_URL, function(err, userdata) {
        userdata.addReservedSession(
            createUserdataParams(providerId, sid, jwtClient),
            function(err, result) {
              if (err) {
                console.log("Unable to send Reservations: " + err);
              } else {
                console.log("Reservation sent successfully: " + result);
              }
            });
      });
    }).catch(function() {
      console.warn('Not sending reserve because of invalid ID:' + uid);
    });
  });
}

/**
 * Send reservation return update to App Server. Clients use App Server to sync.
 *
 * @param uid ID of user returning reservation.
 * @param sid ID of session whose reservation is being returned.
 * @returns {Promise.<TResult>} empty result.
 */
function sendReturn(uid, sid) {
  var jwtClient = getJwtClient();

  return jwtClient.authorize(function (err, tokens) {
    if (err) {
      console.log('Unable to authorize ' + err);
      return;
    }

    return getProviderId(uid).then(function(providerId) {
      return google.discoverAPI(USERDATA_DISCOVERY_URL, function(err, userdata) {
        userdata.removeReservedSession(
            createUserdataParams(providerId, sid, jwtClient),
            function(err, result) {
              if (err) {
                console.log(err);
                console.log("Unable to remove reservation");
              } else {
                console.log("Reservation removed successfully");
              }
            });
      });
    }).catch(function() {
      console.warn('Not sending return because of invalid ID:' + uid);
    });
  });
}

/**
 * Send wiatlist reservation update to App Server. Clients use App Server to
 * sync.
 *
 * @param uid ID of user added to waitlist.
 * @param sid ID of session the user is on the waitlist for.
 * @returns {Promise.<TResult>} empty result.
 */
function sendWaitlist(uid, sid) {
  var jwtClient = getJwtClient();

  return jwtClient.authorize(function (err, tokens) {
    if (err) {
      console.log('Unable to authorize ' + err);
      return;
    }

    return getProviderId(uid).then(function(providerId) {
      return google.discoverAPI(USERDATA_DISCOVERY_URL, function(err, userdata) {
        userdata.addWaitlistedSession(
            createUserdataParams(providerId, sid, jwtClient),
            function(err, result) {
              if (err) {
                console.log(err);
                console.log("Unable to send waitlisted reservation");
              } else {
                console.log("Waitlisted reservation sent successfully");
              }
            });
      });
    }).catch(function() {
      console.warn('Not sending waitlist because of invalid ID:' + uid);
    });
  });
}

/**
 * Get a JWT that is based on the JWT associated with the service account
 * that is allowed to make reservation requests to the App Server. Required
 * JWT values are retrieved from functions config.
 *
 * @returns {google.auth.JWT}
 */
function getJwtClient() {
  var privateKey = functions.config().userdata.key;
  var clientEmail = functions.config().userdata.client_email;
  var scope = functions.config().userdata.scope;

  var jwtClient = new google.auth.JWT(
      clientEmail,
      null,
      privateKey,
      [scope],
      null
  );

  return jwtClient;
}

/**
 * Create parameters to be used when sending reservation updates via the
 * Userdata endpoints.
 *
 * @param providerId Google ID of the user whose reservations are being updated.
 * @param sid ID of session in reservation update.
 * @param jwtClient JWT that is used to authenticate the request to the App
 *                  Server.
 * @returns {
 *            sessionId: string,
 *            userId: string,
 *            timestampUTC: string,
 *            auth: JWT
 *          }
 */
function createUserdataParams(providerId, sid, jwtClient) {
  return {
    sessionId: sid,
    userId: providerId,
    timestampUTC: '' + new Date().getTime(),
    auth: jwtClient
  }
}

/**
 * Get the provider ID that is linked to the FirebaseUser ID.
 *
 * @param uid FirebaseUser ID.
 * @returns {Promise.<TResult>} string provider ID that is linked to the
 *                              FirebaseUser.
 */
function getProviderId(uid) {
  return admin.auth().getUser(uid).then(function(userRecord) {
    if (!userRecord) {
      console.log('Unable to find user record.');
      throw uid;
    }
    var providers = userRecord.providerData;
    for (var i = 0; i < providers.length; i++) {
      var provider = providers[i];
      // Check that provider is Google.
      if (provider.providerId == GOOGLE_PROVIDER_ID) {
        return provider.uid;
      }
    }
    // Return FirebaseUser ID if no Google provider is found.
    return uid;
  }).catch(function(error) {
    console.log('Error fetching user data: ' + error);
    throw uid;
  });
}

// Helper functions for getting database references.
function getSessionReference(sid) {
  return admin.database().ref(PATH_SESSIONS).child(sid);
}

function getSeatsReference(sid) {
  return getSessionReference(sid).child(PATH_SEATS);
}

function getReservationReference(uid, sid) {
  return getSessionReference(sid).child(PATH_RESERVATIONS).child(uid);
}

function getReservationStatusReference(uid, sid) {
  return getReservationReference(uid, sid).child(PATH_STATUS);
}

function getReservationLastStatusChangeReference(uid, sid) {
  return getReservationReference(uid, sid).child(PATH_LAST_STATUS_CHANGED);
}

function getReservationResultReference(uid, sid, rid) {
  return getReservationReference(uid, sid).child(PATH_RESULTS).child(rid);
}

function getQueueReference(uid) {
  return admin.database().ref(PATH_QUEUE).child(uid);
}

function getPromoQueueReference(sid) {
  return admin.database().ref(PATH_PROMO_QUEUE).child(sid);
}
