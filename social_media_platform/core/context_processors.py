from .models import Notification, Connection, Message

def social_context(request):
    if not request.user.is_authenticated:
        return {
            'unread_notifications_count': 0,
            'pending_connections_count': 0,
            'unread_messages_count': 0,
            'user_profile': None,
        }
    
    unread_notifs = Notification.objects.filter(recipient=request.user, is_read=False).count()
    pending_conns = Connection.objects.filter(receiver=request.user, status='pending').count()
    unread_msgs = Message.objects.filter(receiver=request.user, is_read=False).count()
    profile = getattr(request.user, 'profile', None)

    return {
        'unread_notifications_count': unread_notifs,
        'pending_connections_count': pending_conns,
        'unread_messages_count': unread_msgs,
        'user_profile': profile,
    }

