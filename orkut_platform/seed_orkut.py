import os
import django

os.environ.setdefault('DJANGO_SETTINGS_MODULE', 'orkut_network.settings')
django.setup()

from django.contrib.auth.models import User
from django.utils import timezone
from orkut_core.models import (
    UserProfile, FriendRequest, Friendship, StatusPost, PostLike, PostComment,
    ScrapbookMessage, Community, CommunityMember, CommunityPost, CommunityComment,
    PhotoAlbum, Photo, PrivateMessage, ProfileVisitor, Fan, Notification,
    UserRole, RolePermission, UserPermission, AccountDeletionRequest, AdminActivityLog
)

def run():
    print("Starting Orkut database seeding with Roles, Authorities & Admin features...")

    # 0. Seed Roles and Role Permissions
    roles_def = [
        ('SUPER_ADMIN', 'Super Admin', 'Full unrestricted platform access, roles, authorities, user management, and audit inspection.', True),
        ('ADMIN', 'Admin', 'Platform administration, user management, moderation, and content review.', True),
        ('MODERATOR', 'Moderator', 'Community and content moderation, review reported posts and scraps.', True),
        ('NORMAL_USER', 'Normal User', 'Standard registered Orkut networking member.', True)
    ]

    roles_dict = {}
    for code, name, desc, sys in roles_def:
        r, _ = UserRole.objects.get_or_create(code=code, defaults={'name': name, 'description': desc, 'is_system_role': sys})
        r.name = name
        r.description = desc
        r.save()
        roles_dict[code] = r

        rp, _ = RolePermission.objects.get_or_create(role=r)
        if code in ('SUPER_ADMIN', 'ADMIN', 'NORMAL_USER'):
            rp.can_edit_profile = True
            rp.can_upload_photos = True
            rp.can_create_posts = True
            rp.can_manage_posts = True
            rp.can_like_comment = True
            rp.can_send_messages = True
            rp.can_send_friend_requests = True
            rp.can_create_communities = True
            rp.can_join_communities = True
            rp.can_create_albums = True
            rp.can_use_scrapbook = True
            rp.can_access_social_features = True
        elif code == 'MODERATOR':
            rp.can_edit_profile = True
            rp.can_upload_photos = True
            rp.can_create_posts = True
            rp.can_manage_posts = True
            rp.can_like_comment = True
            rp.can_send_messages = True
            rp.can_send_friend_requests = True
            rp.can_create_communities = False
            rp.can_join_communities = True
            rp.can_create_albums = True
            rp.can_use_scrapbook = True
            rp.can_access_social_features = True
        rp.save()

    # 1. Admin User
    admin_user, _ = User.objects.get_or_create(username='admin', defaults={'email': 'admin@orkut.com', 'is_staff': True, 'is_superuser': True})
    admin_user.set_password('Admin@123')
    admin_user.is_staff = True
    admin_user.is_superuser = True
    admin_user.save()

    admin_p, _ = UserProfile.objects.get_or_create(user=admin_user)
    admin_p.full_name = "Orkut Platform Administrator"
    admin_p.profession = "Chief Administrator & Architect"
    admin_p.city = "San Francisco"
    admin_p.country = "USA"
    admin_p.fortune = "Technology connects the world, but friendship powers it."
    admin_p.trusty_rating = 3
    admin_p.cool_rating = 3
    admin_p.sexy_rating = 3
    admin_p.profile_views = 250
    admin_p.is_online = True
    admin_p.account_status = 'ACTIVE'
    admin_p.role = roles_dict['SUPER_ADMIN']
    admin_p.save()

    # 2. Demo Users
    users_data = [
        ('alex_orkut', 'Alex Mercer', 'alex@orkut.com', 'M', 'Mumbai', 'Software Architect', 'Music, Web Dev, Chess', 'Life is too short for bad code.', 'NORMAL_USER'),
        ('rohit_kumar', 'Rohit Kumar', 'rohit@orkut.com', 'M', 'Bengaluru', 'Backend Engineer', 'Cricket, Gaming, AI', 'A smooth sea never made a skilled sailor.', 'NORMAL_USER'),
        ('priya_sharma', 'Priya Sharma', 'priya@orkut.com', 'F', 'Delhi', 'UI/UX Designer', 'Photography, Design, Books', 'Create things that make people smile.', 'MODERATOR'),
        ('neha_verma', 'Neha Verma', 'neha@orkut.com', 'F', 'Pune', 'Data Scientist', 'Yoga, Traveling, Coffee', 'Every day brings a fresh opportunity.', 'NORMAL_USER'),
        ('david_miller', 'David Miller', 'david@orkut.com', 'M', 'London', 'Product Manager', 'Travel, Reading', 'Onward and upward.', 'NORMAL_USER')
    ]

    created_users = []
    for uname, fname, email, gender, city, prof, hobbies, fortune, rcode in users_data:
        u, _ = User.objects.get_or_create(username=uname, defaults={'email': email})
        u.set_password('Password@123')
        u.save()
        p, _ = UserProfile.objects.get_or_create(user=u)
        p.full_name = fname
        p.gender = gender
        p.city = city
        p.profession = prof
        p.interests_hobbies = hobbies
        p.fortune = fortune
        p.trusty_rating = 3
        p.cool_rating = 3
        p.sexy_rating = 2
        p.profile_views = 45
        p.is_online = True
        p.account_status = 'ACTIVE'
        p.role = roles_dict[rcode]
        p.save()
        created_users.append(u)

    alex, rohit, priya, neha, david = created_users

    # 3. Friendships
    def add_friendship(u1, u2):
        Friendship.objects.get_or_create(user1=u1, user2=u2)
        FriendRequest.objects.get_or_create(from_user=u1, to_user=u2, defaults={'status': 'ACCEPTED'})

    add_friendship(admin_user, alex)
    add_friendship(alex, rohit)
    add_friendship(alex, priya)
    add_friendship(alex, neha)
    add_friendship(rohit, priya)
    add_friendship(priya, neha)

    # 4. Status Posts
    p1, _ = StatusPost.objects.get_or_create(
        author=alex,
        content="Welcome back to Orkut! The retro interface brings back so many golden memories of early social networking.",
        link_url="https://github.com"
    )
    p2, _ = StatusPost.objects.get_or_create(
        author=priya,
        content="Designing with clean retro colors today. Orkut blue and magenta make a fantastic aesthetic pairing!",
    )
    p3, _ = StatusPost.objects.get_or_create(
        author=rohit,
        content="Working on full-stack Python and Java search engine integration. Smooth pipelines and instant responses!",
    )

    # Likes & comments
    PostLike.objects.get_or_create(post=p1, user=rohit)
    PostLike.objects.get_or_create(post=p1, user=priya)
    PostLike.objects.get_or_create(post=p1, user=admin_user)
    PostLike.objects.get_or_create(post=p2, user=alex)

    PostComment.objects.get_or_create(post=p1, author=priya, defaults={'content': 'Totally agree! Scrapbook and communities are still unmatched.'})
    PostComment.objects.get_or_create(post=p1, author=rohit, defaults={'content': 'Brings back 2008 vibes! Love this.'})

    # 5. Scraps
    ScrapbookMessage.objects.get_or_create(
        sender=priya,
        recipient=alex,
        defaults={'content': 'Hey Alex! Glad to connect with you on Orkut. Long live scraps!'}
    )
    ScrapbookMessage.objects.get_or_create(
        sender=rohit,
        recipient=alex,
        defaults={'content': 'Dude, check out the new Python & Java communities! Don\'t forget to scrap me back.'}
    )
    ScrapbookMessage.objects.get_or_create(
        sender=neha,
        recipient=alex,
        defaults={'content': 'Happy to see you here Alex! Have a wonderful week ahead.'}
    )

    # 6. Communities
    c1, _ = Community.objects.get_or_create(
        name="Python & Django Programmers",
        defaults={
            'description': 'The premier community for Python web engineers, backend architects, and open source contributors.',
            'category': 'Technology & Internet',
            'creator': admin_user
        }
    )
    c2, _ = Community.objects.get_or_create(
        name="Classic Orkut Nostalgia",
        defaults={
            'description': 'For everyone who misses testies, scraps, cool ratings, and the golden era of social networking.',
            'category': 'Entertainment & Lifestyle',
            'creator': alex
        }
    )
    c3, _ = Community.objects.get_or_create(
        name="Java & Spring Boot Developers",
        defaults={
            'description': 'Enterprise application development, Lucene indexing, and microservices discussion forum.',
            'category': 'Technology & Internet',
            'creator': rohit
        }
    )

    for comm in [c1, c2, c3]:
        for u in [admin_user, alex, rohit, priya, neha, david]:
            CommunityMember.objects.get_or_create(community=comm, user=u, defaults={'role': 'MEMBER', 'status': 'ACTIVE'})

    # Community Topic
    t1, _ = CommunityPost.objects.get_or_create(
        community=c1,
        author=alex,
        title="Best practices for Django database modeling and relations in 2026",
        content="Let's discuss how foreign key prefetching and modular app design make Python social platforms lightning fast."
    )
    CommunityComment.objects.get_or_create(
        topic=t1,
        author=rohit,
        defaults={'content': 'Using select_related for one-to-one and foreign keys keeps query count strictly minimal.'}
    )

    # 7. Photo Albums
    alb1, _ = PhotoAlbum.objects.get_or_create(
        user=alex,
        title="Campus Days & Tech Meetups",
        defaults={'description': 'Memories from developer conferences and university days.'}
    )
    alb2, _ = PhotoAlbum.objects.get_or_create(
        user=priya,
        title="Art & Nature Photography",
        defaults={'description': 'Landscapes and architectural explorations.'}
    )

    # 8. Fans & Visitors
    Fan.objects.get_or_create(user=alex, fan=rohit)
    Fan.objects.get_or_create(user=alex, fan=priya)
    Fan.objects.get_or_create(user=priya, fan=alex)

    ProfileVisitor.objects.get_or_create(user=alex, visitor=priya)
    ProfileVisitor.objects.get_or_create(user=alex, visitor=rohit)
    ProfileVisitor.objects.get_or_create(user=alex, visitor=neha)

    # 9. Private Message
    PrivateMessage.objects.get_or_create(
        sender=priya,
        recipient=alex,
        defaults={'message': 'Hey Alex! Are we meeting for the tech discussion tomorrow?'}
    )
    PrivateMessage.objects.get_or_create(
        sender=alex,
        recipient=priya,
        defaults={'message': 'Yes Priya, at 11 AM! Looking forward to it.'}
    )

    # 10. Notifications
    Notification.objects.get_or_create(
        recipient=alex,
        sender=priya,
        notification_type='SCRAP',
        title="New Scrap Message",
        defaults={'message': 'Priya Sharma left a scrap on your scrapbook!', 'link': f'/scrapbook/{alex.username}/'}
    )

    # 11. Sample Account Deletion Request for Admin Panel Demonstration
    del_req, _ = AccountDeletionRequest.objects.get_or_create(
        user=david,
        defaults={
            'reason': 'Moving to a new personal workspace and requesting account cleanup.',
            'status': 'PENDING'
        }
    )
    david_p = david.orkut_profile
    david_p.account_status = 'PENDING_DELETION'
    david_p.mobile_number = "+1 555-019-2834"
    david_p.save()

    # 12. Sample Administrative Activity Logs
    AdminActivityLog.objects.get_or_create(
        admin_user=admin_user,
        action='SYSTEM_INITIALIZED',
        defaults={
            'affected_user': None,
            'previous_value': 'None',
            'updated_value': 'Orkut System Roles and 12-Authority Framework configured',
            'ip_address': '127.0.0.1'
        }
    )
    AdminActivityLog.objects.get_or_create(
        admin_user=admin_user,
        action='ROLE_ASSIGNED',
        defaults={
            'affected_user': priya,
            'previous_value': 'NORMAL_USER',
            'updated_value': 'MODERATOR',
            'ip_address': '127.0.0.1'
        }
    )

    print("Orkut database seeded successfully with all Roles, Authorities & Deletion features!")

if __name__ == '__main__':
    run()
