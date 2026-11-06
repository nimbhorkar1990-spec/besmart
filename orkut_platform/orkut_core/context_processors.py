from .models import UserProfile, FriendRequest, Friendship, ScrapbookMessage, PrivateMessage, Notification, CommunityMember
from django.db.models import Q

def orkut_context(request):
    if not request.user.is_authenticated:
        return {}

    profile, _ = UserProfile.objects.get_or_create(
        user=request.user,
        defaults={'full_name': request.user.get_full_name() or request.user.username}
    )

    # Pending friend requests to user
    pending_requests_count = FriendRequest.objects.filter(to_user=request.user, status='PENDING').count()

    # Unread notifications
    unread_notifications_count = Notification.objects.filter(recipient=request.user, is_read=False).count()

    # Unread private messages
    unread_messages_count = PrivateMessage.objects.filter(recipient=request.user, is_read=False).count()

    # Unread scraps
    unread_scraps_count = ScrapbookMessage.objects.filter(recipient=request.user, is_read=False).count()

    # User's friends for Right Sidebar "My Friends"
    friendships = Friendship.objects.filter(Q(user1=request.user) | Q(user2=request.user)).select_related('user1', 'user2', 'user1__orkut_profile', 'user2__orkut_profile')
    friends = []
    for f in friendships:
        friend = f.user2 if f.user1 == request.user else f.user1
        # ensure profile exists
        if not hasattr(friend, 'orkut_profile'):
            UserProfile.objects.get_or_create(user=friend, defaults={'full_name': friend.username})
        friends.append(friend)

    # User's communities
    user_communities = CommunityMember.objects.filter(user=request.user, status='ACTIVE').select_related('community')[:6]

    return {
        'current_profile': profile,
        'pending_requests_count': pending_requests_count,
        'unread_notifications_count': unread_notifications_count,
        'unread_messages_count': unread_messages_count,
        'unread_scraps_count': unread_scraps_count,
        'sidebar_friends': friends[:9], # 3x3 grid or list
        'total_friends_count': len(friends),
        'user_communities': [m.community for m in user_communities],
    }
