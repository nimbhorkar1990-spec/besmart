from .models import Notification, Connection, Message, AccountDeletionRequest

def social_context(request):
    if not request.user.is_authenticated:
        return {
            'unread_notifications_count': 0,
            'pending_connections_count': 0,
            'unread_messages_count': 0,
            'user_profile': None,
            'is_admin_user': False,
            'is_super_admin_user': False,
            'pending_deletions_count': 0,
            'user_active_deletion_request': None,
        }
    
    unread_notifs = Notification.objects.filter(recipient=request.user, is_read=False).count()
    pending_conns = Connection.objects.filter(receiver=request.user, status='pending').count()
    unread_msgs = Message.objects.filter(receiver=request.user, is_read=False).count()
    profile = getattr(request.user, 'profile', None)

    is_admin = False
    is_super = False
    pending_deletions = 0
    active_del_req = None

    if profile:
        is_admin = profile.is_admin_or_super()
        is_super = profile.is_super_admin()
        active_del_req = AccountDeletionRequest.objects.filter(
            user=request.user, status__in=['PENDING', 'UNDER_REVIEW', 'APPROVED']
        ).first()

    if is_admin:
        pending_deletions = AccountDeletionRequest.objects.filter(status__in=['PENDING', 'UNDER_REVIEW']).count()

    return {
        'unread_notifications_count': unread_notifs,
        'pending_connections_count': pending_conns,
        'unread_messages_count': unread_msgs,
        'user_profile': profile,
        'is_admin_user': is_admin,
        'is_super_admin_user': is_super,
        'pending_deletions_count': pending_deletions,
        'user_active_deletion_request': active_del_req,
    }

